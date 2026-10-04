public class InterruptTest {
    public static void main(String[] args) throws Exception {
        ResourceManager rm = new ResourceManager(1, 1);
        System.out.println("เริ่ม: " + rm.status());

        // Thread A ถือ PRINTER ไว้ 1.5 วินาที
        Thread a = new Thread(() -> {
            try {
                rm.acquire(ResourceType.PRINTER);
                try { Thread.sleep(1500); }
                finally { rm.release(ResourceType.PRINTER); }
            } catch (InterruptedException e) { }
        }, "A");

        // Thread B รอ PRINTER แล้วถูก interrupt ระหว่างรอ
        Thread b = new Thread(() -> {
            try {
                rm.acquire(ResourceType.PRINTER);   // ยังไม่ได้ permit ถ้าถูก interrupt จะไม่ release
                try { Thread.sleep(500); }
                finally { rm.release(ResourceType.PRINTER); }
            } catch (InterruptedException e) {
                System.out.println("B ถูก interrupt ระหว่างรอ (ไม่ได้ permit จึงไม่ release)");
            }
        }, "B");

        a.start(); Thread.sleep(200);
        b.start(); Thread.sleep(200);
        b.interrupt();
        a.join(); b.join();

        System.out.println("จบ: " + rm.status());   // ต้องเป็น printer=1/1
    }
}