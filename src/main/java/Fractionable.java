public interface Fractionable {
    double doubleValue();
    void setNum(int num);
    void setDenum(int denum);
    void setNumForTest(int num); // добавлено для проверки, чтобы сравнить с желаемым результатом  в CacheTest
    double multiplyValue();
    double sumValue();

    int getSum();
    void setSum(int sum);


}
