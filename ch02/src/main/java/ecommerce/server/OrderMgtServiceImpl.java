package ecommerce.server;

import com.google.protobuf.StringValue;
import ecommerce.OrderManagementGrpc;
import ecommerce.OrderManagementOuterClass;
import io.grpc.stub.StreamObserver;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.*;

public class OrderMgtServiceImpl extends OrderManagementGrpc.OrderManagementImplBase {

    private static final Logger logger = Logger.getLogger(OrderMgtServiceImpl.class.getName());

    /** preparing pseudo order */
    private OrderManagementOuterClass.Order ord1 = OrderManagementOuterClass.Order.newBuilder()
            .setId("102")
            .addItems("Google Pixel 3A").addItems("Mac Book Pro")
            .setDestination("Mountain View, CA")
            .setPrice(1800)
            .build();
    private OrderManagementOuterClass.Order ord2 = OrderManagementOuterClass.Order.newBuilder()
            .setId("103")
            .addItems("Apple Watch S4")
            .setDestination("San Jose, CA")
            .setPrice(400)
            .build();
    private OrderManagementOuterClass.Order ord3 = OrderManagementOuterClass.Order.newBuilder()
            .setId("104")
            .addItems("Google Home Mini").addItems("Google Nest Hub")
            .setDestination("Mountain View, CA")
            .setPrice(400)
            .build();
    private OrderManagementOuterClass.Order ord4 = OrderManagementOuterClass.Order.newBuilder()
            .setId("105")
            .addItems("Amazon Echo")
            .setDestination("San Jose, CA")
            .setPrice(30)
            .build();
    private OrderManagementOuterClass.Order ord5 = OrderManagementOuterClass.Order.newBuilder()
            .setId("106")
            .addItems("Amazon Echo").addItems("Apple iPhone XS")
            .setDestination("Mountain View, CA")
            .setPrice(300)
            .build();
    private Map<String, OrderManagementOuterClass.Order> orderMap = Stream.of(
                    new AbstractMap.SimpleEntry<>(ord1.getId(), ord1),
                    new AbstractMap.SimpleEntry<>(ord2.getId(), ord2),
                    new AbstractMap.SimpleEntry<>(ord3.getId(), ord3),
                    new AbstractMap.SimpleEntry<>(ord4.getId(), ord4),
                    new AbstractMap.SimpleEntry<>(ord5.getId(), ord5))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    /** map to combine multiple orders to a shipment */
    private Map<String, OrderManagementOuterClass.CombinedShipment> combinedShipmentMap = new HashMap<>();
    /** batch size used to combine orders to a shipment */
    public static final int BATCH_SIZE = 3;


    // Unary
    @Override
    public void addOrder(OrderManagementOuterClass.Order request, StreamObserver<StringValue> responseObserver) {
        logger.info("Order Added - ID: " + request.getId() + ", Destination : " + request.getDestination());
        orderMap.put(request.getId(), request);
        StringValue id = StringValue.newBuilder().setValue("100500").build();
        responseObserver.onNext(id); // response to the client
        responseObserver.onCompleted(); // send nil to indicate the transmission termination
    }

    // Unary
    @Override
    public void getOrder(StringValue request, StreamObserver<OrderManagementOuterClass.Order> responseObserver) {
        OrderManagementOuterClass.Order order = orderMap.get(request.getValue());
        if (order != null) {
            System.out.printf("Order Retrieved : ID - %s", order.getId());
            responseObserver.onNext(order); // response to the client
            responseObserver.onCompleted(); // send nil to indicate the transmission termination
        } else  {
            logger.info("Order : " + request.getValue() + " - Not found.");
            responseObserver.onCompleted(); // send nil to indicate the transmission termination
        }
        // ToDo  Handle errors
        // responseObserver.onError();
    }

    // Server Streaming
    @Override
    public void searchOrders(StringValue request, StreamObserver<OrderManagementOuterClass.Order> responseObserver) {

        for (Map.Entry<String, OrderManagementOuterClass.Order> orderEntry : orderMap.entrySet()) {
            OrderManagementOuterClass.Order order = orderEntry.getValue();
            int itemsCount = order.getItemsCount();
            for (int index = 0; index < itemsCount; index++) {
                String item = order.getItems(index);
                if (item.contains(request.getValue())) {
                    logger.info("Item found " + item);
                    responseObserver.onNext(order); // response multiple orders to the client
                    break;
                }
            }
        }
        responseObserver.onCompleted(); // send nil to indicate the transmission termination
    }

    // Client Streaming
    @Override
    public StreamObserver<OrderManagementOuterClass.Order> updateOrders(StreamObserver<StringValue> responseObserver) {
        return new StreamObserver<OrderManagementOuterClass.Order>() {
            StringBuilder updatedOrderStrBuilder = new StringBuilder().append("Updated Order IDs : ");

            @Override
            public void onNext(OrderManagementOuterClass.Order value) { // the client streaming can send multiple onNext
                if (value != null) {
                    orderMap.put(value.getId(), value);
                    updatedOrderStrBuilder.append(value.getId()).append(", ");
                    logger.info("Order ID : " + value.getId() + " - Updated");
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.info("Order ID update error " + t.getMessage());
            }

            @Override
            public void onCompleted() { // the client has sent nil
                logger.info("Update orders - Completed");
                StringValue updatedOrders = StringValue.newBuilder().setValue(updatedOrderStrBuilder.toString()).build();
                responseObserver.onNext(updatedOrders);
                responseObserver.onCompleted();
            }
        };
    }


    // Bi-di Streaming
    @Override
    public StreamObserver<StringValue> processOrders(StreamObserver<OrderManagementOuterClass.CombinedShipment> responseObserver) {

        return new StreamObserver<StringValue>() {
            int batchMarker = 0;
            @Override
            public void onNext(StringValue value) {
                logger.info("Order Proc : ID - " + value.getValue());
                OrderManagementOuterClass.Order currentOrder = orderMap.get(value.getValue());
                if (currentOrder == null) {
                    logger.info("No order found. ID - " + value.getValue());
                    return;
                }
                // Processing an order and increment batch marker to
                batchMarker++;
                String orderDestination = currentOrder.getDestination();
                OrderManagementOuterClass.CombinedShipment existingShipment = combinedShipmentMap.get(orderDestination);

                if (existingShipment != null) {
                    existingShipment = OrderManagementOuterClass.CombinedShipment.newBuilder(existingShipment).addOrdersList(currentOrder).build();
                    combinedShipmentMap.put(orderDestination, existingShipment);
                } else {
                    OrderManagementOuterClass.CombinedShipment shipment = OrderManagementOuterClass.CombinedShipment.newBuilder().build();
                    shipment = shipment.newBuilderForType()
                            .addOrdersList(currentOrder)
                            .setId("CMB-" + new Random().nextInt(1000)+ ":" + currentOrder.getDestination())
                            .setStatus("Processed!")
                            .build();
                    combinedShipmentMap.put(currentOrder.getDestination(), shipment);
                }

                if (batchMarker == BATCH_SIZE) {
                    // Order batch completed. Flush all existing shipments.
                    for (Map.Entry<String, OrderManagementOuterClass.CombinedShipment> entry : combinedShipmentMap.entrySet()) {
                        responseObserver.onNext(entry.getValue());
                    }
                    // Reset batch marker
                    batchMarker = 0;
                    combinedShipmentMap.clear();
                }
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onCompleted() { // send all remaining orders in the incomplete shipment
                for (Map.Entry<String, OrderManagementOuterClass.CombinedShipment> entry : combinedShipmentMap.entrySet()) {
                    responseObserver.onNext(entry.getValue());
                }
                responseObserver.onCompleted();
            }

        };
    }
}
