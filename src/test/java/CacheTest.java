import jdk.jfr.Description;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CacheTest {
    //Добавлено тестирование для 3 задачи все предыдущие тесты от 2 задания проверяем тоже, они должны работать
    @Test
    @Description("Вызов используя класс Fraction")
    void doubleFractionTask3Test(){
        FractionTest fractionTest = new FractionTest(2, 3);
        Fractionable numProxy = Utility.Cache(fractionTest);
        numProxy.doubleValue();
        Assertions.assertEquals(1, fractionTest.cmpCnt);// значение пересчитываем
        numProxy.doubleValue();
        Assertions.assertEquals(1, fractionTest.cmpCnt); // значение не изменилось (вызов из кэша)

        numProxy.setNum(5);
        Assertions.assertEquals(1, fractionTest.cmpCntMutator); // вызов метода, помеченного аннотацией Mutator
        Assertions.assertEquals(1, fractionTest.cmpCnt); // значение не изменилось после вызова метода Mutator

        numProxy.doubleValue();
        Assertions.assertEquals(2, fractionTest.cmpCnt); // значение пересчитываем
        numProxy.doubleValue();
        Assertions.assertEquals(2, fractionTest.cmpCnt); // значение не изменилось (вызов из кэша)

        numProxy.setNum(2);
        Assertions.assertEquals(2, fractionTest.cmpCntMutator); // вызов метода, помеченного аннотацией Mutator
        numProxy.doubleValue();
        Assertions.assertEquals(2, fractionTest.cmpCnt); // значение не изменилось (вызов из кэша)
        numProxy.doubleValue();
        Assertions.assertEquals(2, fractionTest.cmpCnt); // значение не изменилось (вызов из кэша)
        // Ждем , чтобы кэш очистить
        CountDownLatch waiter = new CountDownLatch(1);
        try {
            waiter.await(1500* 1, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        numProxy.doubleValue();
        Assertions.assertEquals(3, fractionTest.cmpCnt); // значение пересчитываем
        numProxy.doubleValue();
        Assertions.assertEquals(3, fractionTest.cmpCnt); // значение не изменилось (вызов из кэша)
        // изменяем другое поле
        numProxy.setSum(5);
        numProxy.doubleValue();
        Assertions.assertEquals(3, fractionTest.cmpCnt); // значение не изменилось (вызов из кэша)
        numProxy.sumValue();
        Assertions.assertEquals(3, fractionTest.cmpCnt); // значение не изменилось (вызов из кэша)

        numProxy.setNum(9);
        numProxy.doubleValue();
        Assertions.assertEquals(4, fractionTest.cmpCnt); // значение изменилось
        Assertions.assertEquals(3, fractionTest.cmpCntMutator); // значение изменилось

        numProxy.multiplyValue();
        Assertions.assertEquals(1, fractionTest.cmpCntMulti); // значение увеличилось на 1
        numProxy.multiplyValue();
        Assertions.assertEquals(1, fractionTest.cmpCntMulti); // значение не изменилось
        Assertions.assertEquals(4, fractionTest.cmpCnt); // значение не изменилось
    }


    //  Добавлено тестирование с использованием тестового класса
    @Test
    @Description("Вызов метода без аннотации. Тестирование с использованием  тестового класса")
    void doubleValueFractionTest(){

        FractionTest fractionTest = new FractionTest(20, 4);
        Fractionable fr = Utility.Cache(fractionTest);
        for(int i = 0; i < 4; i++)
            fr.toString();
        Assertions.assertEquals(4, fractionTest.cmpCnt);
    }
    @Test
    @Description("Вызов метода c аннотацией Mutator. Тестирование с использованием  тестового класса")
    void doubleValueFractionTestMutator(){
        FractionTest fractionTest = new FractionTest(20, 4);
        Fractionable fr = Utility.Cache(fractionTest);
        for(int i = 0; i < 4; i++)
            fr.setNum(40);
        Assertions.assertEquals(0, fractionTest.cmpCnt);
        Assertions.assertEquals(4, fractionTest.cmpCntMutator);
    }

    @Test
    @Description("Вызов метода c аннотацией Cache. Тестирование с использованием  тестового класса")
    void doubleValueFractionTestCache(){
        FractionTest fractionTest = new FractionTest(20, 4);
        Fractionable fr = Utility.Cache(fractionTest);
        for(int i = 0; i < 4; i++)
            fr.doubleValue();
        Assertions.assertEquals(1, fractionTest.cmpCnt);
    }
    @Test
    @Description("Вызов методов. Тестирование с использованием  тестового класса")
    void doubleValueFractionTestMix(){

        FractionTest fractionTest = new FractionTest(20, 4);
        Fractionable fr = Utility.Cache(fractionTest);
        fr.doubleValue();
        fr.doubleValue();
        Assertions.assertEquals(1, fractionTest.cmpCnt);
        fr.toString();
        fr.toString();
        Assertions.assertEquals(3, fractionTest.cmpCnt);
        fr.doubleValue();
        Assertions.assertEquals(3, fractionTest.cmpCnt);
        // Вызов метода, помеченного аннотацией Mutator
        fr.setNum(40);
        Assertions.assertEquals(3, fractionTest.cmpCnt);
        Assertions.assertEquals(1, fractionTest.cmpCntMutator);

        fr.doubleValue();
        Assertions.assertEquals(4, fractionTest.cmpCnt);
        fr.doubleValue();
        fr.doubleValue();
        Assertions.assertEquals(4, fractionTest.cmpCnt);

    }

    // Тестирование с использованием базового класса
    @Test
    @Description("Тестирование значений в кэше")
    void DoubleValue() {
        Fractionable numProxy = Utility.Cache(new Fraction(10,2));
        // Проверяем для первого метода @Cache(их м.б. несколько)
        Assertions.assertEquals(5, numProxy.doubleValue());
        numProxy.doubleValue(); // молчит
        numProxy.doubleValue(); // молчит
        numProxy.setNum(20);
        Assertions.assertEquals(10, numProxy.doubleValue());
        numProxy.doubleValue(); // сработал
        Assertions.assertEquals(10, numProxy.doubleValue());
        numProxy.doubleValue(); // молчит
        Assertions.assertEquals(10, numProxy.doubleValue());

        // В реализацию для тестирования добавили метод setNumForTest НЕ ПОМЕЧЕННЫЙ аннотацией
        // , т.к. считаем, что только метод, помеченный аннот. Mutator изменяет значение
        numProxy.setNumForTest(2);
        // если из кэша, то получим 10 иначе 1
        Assertions.assertNotEquals(1, numProxy.doubleValue());


        // Проверяем для след. метода @Cache(их м.б. несколько)
        numProxy.setNum(2);
        numProxy.setDenum(4);
        Assertions.assertEquals(8, numProxy.multiplyValue());
        numProxy.doubleValue(); // молчит
        Assertions.assertEquals(8, numProxy.multiplyValue());
        numProxy.doubleValue(); // молчит
        Assertions.assertEquals(8, numProxy.multiplyValue());
        numProxy.setNum(4);
        Assertions.assertEquals(16, numProxy.multiplyValue());
        numProxy.doubleValue(); // сработал
        Assertions.assertEquals(16, numProxy.multiplyValue());
        numProxy.doubleValue(); // молчит
        Assertions.assertEquals(16, numProxy.multiplyValue());

        // В реализацию для тестирования добавили метод НЕ ПОМЕЧЕННЫЙ аннотацией, т.к. считаем, что только метод, помеченный аннот. Mutator изменяет значение
        numProxy.setNumForTest(5);
       Assertions.assertEquals(20.0, numProxy.multiplyValue());

    }




}

