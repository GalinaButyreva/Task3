import java.lang.reflect.Proxy;

public class Utility {
    // Метод Cache
    public static <T> T Cache(T  fr)  {
        CacheInvocationHandler cacheInvocationHandler =  new CacheInvocationHandler(fr);
        Class[] interfaces = fr.getClass().getInterfaces();
        T proxyObject = (T) Proxy.newProxyInstance(Fraction.class.getClassLoader(), new Class[] {interfaces[0]} , cacheInvocationHandler);
        return proxyObject;
    }
}