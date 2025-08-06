package example;

public class ConcolicTests {
    public static int lol(int a) {
        int b = a;
        int c = 3;
        int g = a == 5 ? 4 : b;
        foo(b, c);
        foo(c, c);
        foo(g, c);
        foo(g, b);

        if (b > 5) {
            b = 5 + 9;
        }
        D d = new D();
        d.d = b;
        foo(d.d, g);

        int[] arr = new int[10];
        arr[0] = a;
        foo(arr[0], 5);

        try {
            exception(a);
        } catch (Throwable e) {
            test(e, a);
            foo(a, 6);
        }

        return b;
    }

    public static int lolD(D dd) {
        int a = dd.d;
        int b = a;
        int c = 3;
        int g = a == 5 ? 4 : b;
        foo(b, c);
        foo(c, c);
        foo(g, c);
        foo(g, b);

        if (b > 5) {
            b = 5 + 9;
        }
        D d = new D();
        foo(d.d, c);
        d.d = b;
        foo(d.d, g);

        int[] arr = new int[10];
        foo(arr[0], 3);
        arr[0] = a;
        foo(arr[0], 5);

        try {
            exception(a);
        } catch (Throwable e) {
            test(e, a);
            foo(a, 6);
        }

        return b;
    }

    public static int lolol(int a) {
        int b = a;
        int c = 3;
        switch (b) {
            case 5: return 2;
            case 6: return 3;
            case 7: return 4;
        }
        int d = addFive(c);
        int e = addFive(b);

        if (b < 10) {
            e += 18;
        }

        return d + e;
    }

    private static int addFive(int c) {
        return c + 5;
    }

    private static void foo(int i, int j) {
        bar(j, new D());
    }

    private static void bar(int i, D d) {

    }

    private static void exception(int i) throws MyException {
        throw new MyException("foo");
    }

    private static void test(Throwable t, int i) {

    }

    private static class MyException extends Exception {
        private static final long serialVersionUID = -3387516993124229948L;

        private static String s;
        public MyException(String foo) {
            s = foo;
        }
    }

    static class D {
        int d;
    }
}

