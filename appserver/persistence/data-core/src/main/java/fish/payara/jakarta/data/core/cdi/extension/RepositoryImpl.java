package fish.payara.jakarta.data.core.cdi.extension;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.logging.Logger;


public class RepositoryImpl<T> implements InvocationHandler {
    
    public static final Logger logger = Logger.getLogger(RepositoryImpl.class.getName());
    
    final Class<T> repositoryInterface;
    
    public RepositoryImpl(Class<T> repositoryInterface) {
        this.repositoryInterface = repositoryInterface;
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        logger.info("executing method:"+method.getName());
       return "executing method:"+method.getName();
    }
    
}