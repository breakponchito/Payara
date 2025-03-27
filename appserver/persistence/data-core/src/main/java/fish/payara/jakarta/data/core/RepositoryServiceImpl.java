package fish.payara.jakarta.data.core;

import jakarta.data.repository.Repository;
import java.util.concurrent.ConcurrentHashMap;
import org.glassfish.apf.AnnotationInfo;
import org.glassfish.api.StartupRunLevel;
import org.glassfish.hk2.runlevel.RunLevel;
import org.jvnet.hk2.annotations.Service;


@Service(name = "repository-service")
@RunLevel(StartupRunLevel.VAL)
public class RepositoryServiceImpl implements RepositoryService {

    private ConcurrentHashMap<Class<?>, AnnotationInfo> repositoryMap = new ConcurrentHashMap<>();


    @Override
    public ConcurrentHashMap<Class<?>, AnnotationInfo> getRepositoryMap() {
        return repositoryMap;
    }
}
