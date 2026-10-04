
import java.util.concurrent.Semaphore;

/**
 * ควบคุมสิทธิ์การใช้ทรัพยากรร่วมของทั้งระบบ
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ข้อกำหนดจากโจทย์ที่เกี่ยวกับคลาสนี้: - หัวข้อ 5: ใช้ Semaphore ควบคุม PRINTER
 * และ DATABASE จำนวน permit มาจาก command line (Config) ในส่วนบังคับให้สร้าง
 * Semaphore แบบ fair = true - Worker ทุกตัวต้องใช้ ResourceManager object
 * เดียวกัน - หัวข้อ 7: permit ต้องไม่สูญหายหรือค้าง แม้เกิด exception หรือถูก
 * interrupt ระหว่างถือ resource
 *
 * คำถามที่จะถูกถามใน Demo: - ทำไมต้อง fair = true และถ้าเปลี่ยนเป็น false
 * จะเกิดอะไรขึ้น - ถ้า Thread ถูก interrupt หลัง acquire สำเร็จแต่ก่อน release
 * โค้ดของกลุ่มยังคืน permit ได้หรือไม่
 */
public class ResourceManager {

    // TODO: เก็บ Semaphore ของ PRINTER และ DATABASE
    private final Semaphore printer;
    private final Semaphore database;
    private final int printerTotal;
    private final int databaseTotal;

    public ResourceManager(int printerPermits, int databasePermits) {
        // TODO
        this.printerTotal = printerPermits;
        this.databaseTotal = databasePermits;
        this.printer = new Semaphore(printerPermits, true);    // fair = true
        this.database = new Semaphore(databasePermits, true);  // fair = true
    }

    /**
     * ขอสิทธิ์ใช้ทรัพยากร จะรอจนกว่าจะได้
     */
    public void acquire(ResourceType type) throws InterruptedException {
        // TODO
        Semaphore s = semaphoreOf(type);
        if (s != null) {
            s.acquire();
        }
    }

    /**
     * คืนสิทธิ์ใช้ทรัพยากร
     */
    public void release(ResourceType type) {
        // TODO
        Semaphore s = semaphoreOf(type);
        if (s != null) {
            s.release();
        }
    }

    /**
     * ข้อความสั้น ๆ บอกสถานะการใช้ทรัพยากร สำหรับส่งให้ ProjectLogger.monitor()
     * เช่น "printer=1/1 database=0/2"
     */
    public String status() {
        // TODO
        return "printer=" + printer.availablePermits() + "/" + printerTotal
                + " database=" + database.availablePermits() + "/" + databaseTotal;
    }

    /**
     * helper: คืน Semaphore ตามชนิด ถ้าเป็น NONE คืน null
     */
    private Semaphore semaphoreOf(ResourceType type) {
        switch (type) {
            case PRINTER:
                return printer;
            case DATABASE:
                return database;
            default:
                return null;
        }
    }

}
