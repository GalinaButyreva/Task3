import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import java.util.*;
import java.util.concurrent.*;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;


public class CacheInvocationHandler implements InvocationHandler {
    volatile Thread mainThread = Thread.currentThread();
    // Имплементация интерфейса
    private final Object  targetObject;
    private   Boolean changed = true;
    // Храним состояния
    private final Map<Method, Map<SnapshotFieldInterface, Object>>  methodCachesMap = new HashMap<>();

    // Выбран ConcurrentHashMap, т. к. измненение HashMap в нескольких потоках  нарушит структуру HashMap
    // Общую структуру данных можно защитить, установив блокировку, однако проще выбрать потокобезопасную структуру
    private final ConcurrentHashMap<Method, Long> methodCacheLiveMap = new ConcurrentHashMap<>(); // коллекция для отслеживания времени жизни


    // Долго мучилась(сначала сделала и проверила, потом после добавления нового поля и его изменения тест отрабатывает неправильно)
    // если добавляем новое поле и пытаемся изменить значение, результат не соответствует ожидаемому
    // Добавление полей в неотслеживаемые помогает решить проблему
    // Не отслеживаемыми считаются все поля, состояние которых меняется НЕ методом, помеченным как Mutator
    // а также поля, которые помечены аннотацией NotSaveState (это удобно при тестировании тестового класса в тестах,
    // т.к. не отследить изменение cmpCntMutator (счетчик вызовов Mutator метода все остальные можно) )
    List<String> fieldsExclude = new ArrayList<>(); // Исключаем не отслеживаемые поля (удобно при тестировании FractionTest  класса)

   // Добавить метод с анноттацией Cache  в коллекцию methodCacheLiveMap
    public void addMethodCacheLiveMap(Method method, Integer timeliveMeth)  {
       long endTime = 0;
       if (timeliveMeth > 0)
           endTime = System.currentTimeMillis() + timeliveMeth;
       methodCacheLiveMap.put(method, endTime);
    }

    // Сохранить  в историю состояний
    private void saveState(boolean changed, Method method, Object resultOfInvocation) throws IllegalAccessException {
       if (changed){
           // Сохраним состояние
            Map<SnapshotFieldInterface, Object>  mapSnap; // для состояний
            // Определим, что метод выз-ся первый раз иначе найдем историю состояний
            if (methodCachesMap.isEmpty() || !methodCachesMap.containsKey(method)) {
                mapSnap = new HashMap<>();
            }
            else {
                mapSnap = methodCachesMap.get(method);
            }
            mapSnap.put(new SnapShotField(), resultOfInvocation);
            methodCachesMap.put(method, mapSnap);
       }
    }
    // Почистить историю сохранений
     private void clearState(Method method){
         if (!methodCachesMap.isEmpty()) {
             methodCachesMap.remove(method);
         }
     }
    // Найдем ззначение   в истории сохраненных состояний (результат совпадение с текущим отслеживаемым состоянием)
    private Object findResultFromCache(Method method) throws IllegalAccessException {
        Object resultOfInvocation = null;
        if (!methodCachesMap.isEmpty() && methodCachesMap.containsKey(method))
        {
            Map<SnapshotFieldInterface, Object>   mapSnap = methodCachesMap.get(method);
        // Найдем среди  сохраненных состояний результат совпадение с текущим состоянием
            for(Map.Entry<SnapshotFieldInterface, Object>  entry : mapSnap.entrySet()){
                SnapshotFieldInterface snapshot = entry.getKey();
                if (snapshot.compareSate())
                    resultOfInvocation = mapSnap.get(snapshot);
            }
        }
        return  resultOfInvocation;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
         Object  resultOfInvocation;                  // результат выполнения
        //Метод с аннотациями
        Method curMethod = targetObject.getClass().getMethod(method.getName(),method.getParameterTypes());
        // Если вызван метод  с аннот. CACHE(присутствует аннотация Cache)
        if (curMethod.isAnnotationPresent(Cache.class))
        {
            // Проверяем не вышло ли время хранения в кэше
            if (!methodCacheLiveMap.isEmpty() && methodCacheLiveMap.containsKey(method)){
                resultOfInvocation = findResultFromCache(method);
                // Не нашли в кэше
                if (resultOfInvocation == null) {
                    resultOfInvocation = method.invoke(targetObject, args);
                    // сохраним состояние
                    saveState(changed, method, resultOfInvocation); // сохраняем состояние
                    System.out.println("Значение вычислено(не нашли среди состояний) = " + (double) resultOfInvocation);
                }else{
                    System.out.println("Вызов из кэша = " + (double) resultOfInvocation);}
            } else {
                // время хранения вышло(метода нет в коллекции времени жизни объекта)
                resultOfInvocation = method.invoke(targetObject, args);
                //  Вывод инфо о вычислении (можно было упростить убрав лишний вывод, однако, для понимания что происходит осатвлен)
                if (methodCachesMap.isEmpty() || !methodCachesMap.containsKey(method) ) {
                    System.out.println("Значение вычислено == " + (double) resultOfInvocation);
                }else{
                    if (methodCacheLiveMap.isEmpty() || !methodCacheLiveMap.containsKey(method)) {
                        clearState(method); // почистим историю состояний (т.к. время хранения в кэше вышло)
                        System.out.println("Значение вычислено(время хранения в кэш вышло ) = " + (double) resultOfInvocation);
                    }
                    else{
                        System.out.println("Значение вычислено = " + (double) resultOfInvocation);
                     }
                }
                saveState(true, method, resultOfInvocation); // сохраняем состояние
            }
            changed = false;
            // При востребовании значения срок жизни обновляется
            addMethodCacheLiveMap(method, curMethod.getAnnotation(Cache.class).value());
        }
        else {
            // Для сравнения состояний , чтобы найти не отслеживаемые поля
            SnapShotField curSnapBefore = new SnapShotField();

            // Вычисление значения или выполнение во всех остальных случаях
            resultOfInvocation = method.invoke(targetObject, args);

            SnapShotField curSnapAfter = new SnapShotField();
            // Зафиксируем вызов метода Mutator
            if (curMethod.isAnnotationPresent(Mutator.class)) {
                changed = true;
                System.out.println("\n Вызов метода , помеченного аннотацией Mutator ");
            }
            else
            {
                // Если вдруг появилось новое поле, которое не надо отслеживать, фиксируем его
                curSnapAfter.fieldsExcludeAddRemove(curSnapBefore.getFieldsSave());
            }
        }
        // Вернем результат
        return resultOfInvocation;
    }

    // Попытка остановить поток
    private void shutdownExecutor(ExecutorService executorSrv) {
        executorSrv.shutdown();
        try { if (! executorSrv.awaitTermination(10000, TimeUnit.MILLISECONDS))
            executorSrv.shutdownNow();
        }
        catch (InterruptedException ex){
            executorSrv.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Выполнить удаление из кэша не востребованных значений за время жизни
    private void  RunThreadClear(){
       // Executor - интерфейс, который может выполнять задачи не вникания в детали использования потока
        //  его расширением явл-ся более слжный интерфейс ExecutorService с дополнительными методами , его имплементацией явл-ся ThreadPoolExecutor
        // Интерфейс ScheduledExecutorService  может планировать операции для исполнения или исполнять периодически
        // для периодического выполнения кода
        ScheduledExecutorService executorSrv = newSingleThreadScheduledExecutor();
        executorSrv.scheduleWithFixedDelay(()->{
                    if (mainThread.isAlive())
                        methodCacheLiveMap.entrySet().removeIf(e -> e.getValue() != 0 && e.getValue() <= System.currentTimeMillis());
                    else
                        shutdownExecutor(executorSrv);
                        //executorSrv.shutdownNow();

                }
                , 0,1, TimeUnit.MILLISECONDS);

    }

    // Конструктор
    public CacheInvocationHandler(Object targetObject) {
        this.targetObject = targetObject;
        // Начитаем пполя, состояние которых не сохраняем
        Field[] fields = targetObject.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (!field.isAccessible())
                field.setAccessible(true);
            if (field.isAnnotationPresent(NotSaveState.class))
                fieldsExclude.add(field.getName());
        }

        // Запускаем 2-й поток
       RunThreadClear();
    }

    // Класс для хранения состояния
    // Можно было просто классом сделать без интерфейса(мне хотелось исп-ть интерфейс)
    class SnapShotField implements SnapshotFieldInterface {
        private final Map<String, Object> fieldsSave = new HashMap<>();
        // Фиксируем  значения полей, состояние которых не отслеживаем
        private void fieldsExcludeAddRemove(Map<String, Object>  fieldsCmp){
            if (!fieldsCmp.equals(fieldsSave))
                for (String keySv : fieldsCmp.keySet()){
                    Object keySvVal = fieldsCmp.get(keySv);
                    for (String keyCmp : fieldsSave.keySet()) {
                        if (keyCmp.equals(keySv) && !fieldsSave.get(keyCmp).equals(keySvVal)){
                            if (!fieldsExclude.contains(keyCmp))
                                fieldsExclude.add(keyCmp);
                        }
                    }
            }
        }

        public Map<String, Object> getFieldsSave() {
            return fieldsSave;
        }

        // Конструктор
        public  SnapShotField() throws IllegalAccessException {
            Field[] fields = targetObject.getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAccessible())
                    field.setAccessible(true);
                fieldsSave.put(field.getName(), field.get(targetObject));
            }
        }

        @Override
        //  Сравнить с текущим состоянием(найти в истории совпадающее с текущим состоянием)
        public Boolean compareSate() throws IllegalAccessException {
            Field[] fieldsCur = targetObject.getClass().getDeclaredFields();
            for (Field field : fieldsCur) {
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }

                if (
                       (fieldsExclude.isEmpty() || !fieldsExclude.contains(field.getName())) && // Исключаем не отслеживаемые поля
                         !(fieldsSave.containsKey(field.getName())
                                && fieldsSave.get(field.getName()).equals(field.get(targetObject)) // проверим на совпадение
                         )
                )
                    return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "SnapShotField{" +
                    "fieldsSave=" + fieldsSave +
                    '}';
        }
    }
  /*
    // оставлено для отладки
      public void viewMethodCacheLiveMap(){
        for(Map.Entry<Method, Long> entry : methodCacheLiveMap.entrySet() ) {
            System.out.println("Entry " + entry.getKey().getName() + " " + entry.getValue()
                        + " curTime = " + System.currentTimeMillis()
                        + " diff = " + (System.currentTimeMillis() - entry.getValue()));
        }
    }
     public  void viewMethodCachesMap(Method method) {
        if (!methodCachesMap.isEmpty()  && methodCachesMap.containsKey(method)){
            Map<SnapshotFieldInterface, Object>  mapSnap = methodCachesMap.get(method);
            System.out.println(mapSnap);
        }
    }
*/

}


