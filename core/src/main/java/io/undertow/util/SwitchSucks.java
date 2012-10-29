package io.undertow.util;


public class SwitchSucks {

    private static final int ITERATIONS = 2000000000;

    public static void main(String[] args) {
        for (; ; ) {
            double r1 = tswitch();
            double r2 = ifelse();

            System.out.println(r1 + " vs " + r2 + " (" + ((1.0 - (r2 / r1)) * 100.0) + "%)");
        }


    }

    public static double tswitch() {
        int branch = 0;
        byte[] b = new byte[6];

        long sum = 0;
        for (int j = 0; j < 5; j++) {
            long time = System.currentTimeMillis();
            for (int i = 0; i < ITERATIONS; i++) {

                switch (branch) {
                    case 0:
                        b[branch++] = (byte) i;
                        break;
                    case 1:
                        b[branch++] = (byte) (i << 3);
                        break;
                    case 2:
                        b[branch++] = (byte) (i << 8);
                        break;
                    case 3:
                        b[branch++] = (byte) (i << 7);
                        break;
                    case 4:
                        b[branch++] = (byte) (i << 6);
                        break;
                    case 5:
                        b[branch] = (byte) (i << 5);
                        branch = 0;
                        break;
                }
            }
            long delta = System.currentTimeMillis() - time;
            int proof = b[0] + b[1] + b[2];

            if (j > 0) {
                sum += delta;
            } else {
                System.out.print("Warmup: ");
            }
            System.out.print("[Proof " + proof + "] ");
            System.out.println(delta);
        }
        double avg = sum / 4.0;
        System.out.println("Tableswitch avg: " + avg);
        return avg;
    }

    public static double ifelse() {
        int branch = 0;
        byte[] b = new byte[6];

        long sum = 0;

        for (int j = 0; j < 5; j++) {
            long time = System.currentTimeMillis();
            for (int i = 0; i < ITERATIONS; i++) {
                if (branch == 0) {
                    b[branch++] = (byte) i;
                } else if (branch == 1) {
                    b[branch++] = (byte) (i << 3);
                } else if (branch == 2) {
                    b[branch] = (byte) (i << 8);
                    branch = 0;
                } else if (branch == 3) {
                    b[branch++] = (byte) (i << 7);
                } else if (branch == 4) {
                    b[branch] = (byte) (i << 6);
                    branch = 0;
                } else if (branch == 5) {
                    b[branch] = (byte) (i << 5);
                    branch = 0;
                }
            }
            long delta = System.currentTimeMillis() - time;
            int proof = b[0] + b[1] + b[2];
            if (j > 0) {
                sum += delta;
            } else {
                System.out.print("Warmup: ");
            }
            System.out.print("[Proof " + proof + "] ");

            System.out.println(delta);
        }
        double avg = sum / 4.0;
        System.out.println("if-else avg: " + avg);

        return avg;
    }

}