package fish.payara.jakarta.data.core;

import jakarta.data.repository.Repository;
import jakarta.enterprise.inject.spi.AnnotatedType;
import java.util.concurrent.ConcurrentHashMap;
import org.glassfish.apf.AnnotationInfo;
import org.jvnet.hk2.annotations.Contract;

@Contract
public interface RepositoryService {

    ConcurrentHashMap<Class<?>, AnnotationInfo> getRepositoryMap();
    
}
