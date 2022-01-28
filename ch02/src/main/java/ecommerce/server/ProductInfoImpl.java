package ecommerce.server;

import ecommerce.ProductInfoGrpc;
import ecommerce.ProtoInfo;
import ecommerce.client.ProductInfoClient;
import io.grpc.Status;
import io.grpc.StatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class ProductInfoImpl extends ProductInfoGrpc.ProductInfoImplBase {

    private Map productMap = new HashMap<String, ProtoInfo.Product>();
    private static final Logger logger = Logger.getLogger(ProductInfoClient.class.getName());

    @Override
    public void addProduct(ProtoInfo.Product request,
                           io.grpc.stub.StreamObserver<ProtoInfo.ProductID> responseObserver) {
        UUID uuid = UUID.randomUUID();
        String randomUUIDString = uuid.toString();
        request = request.toBuilder().setId(randomUUIDString).build();
        productMap.put(randomUUIDString, request);
        ProtoInfo.ProductID id
                = ProtoInfo.ProductID.newBuilder().setValue(randomUUIDString).build();
        logger.info("addProduct");
        responseObserver.onNext(id);
        responseObserver.onCompleted();
    }

    @Override
    public void getProduct(ProtoInfo.ProductID request,
                           io.grpc.stub.StreamObserver<ProtoInfo.Product> responseObserver) {
        String id = request.getValue();
        logger.info("getProduct");
        if (productMap.containsKey(id)) {
            responseObserver.onNext((ProtoInfo.Product) productMap.get(id));
            responseObserver.onCompleted();
        } else {
            responseObserver.onError(new StatusException(Status.NOT_FOUND));
        }
    }
}