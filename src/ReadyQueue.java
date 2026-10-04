
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * คิวงานที่พร้อมถูกหยิบไปทำ
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * สิ่งที่คลาสนี้ต้องทำได้: - เก็บงานที่รอ Worker อยู่ -
 * หยิบงานถัดไปตามนโยบายที่เลือก (FCFS หรือ Priority) - ถูกเรียกจากหลาย Thread
 * พร้อมกันได้อย่างปลอดภัย
 *
 * ข้อกำหนดจากโจทย์ที่เกี่ยวกับคลาสนี้: - หัวข้อ 4: priority = 1 สูงสุด
 * เมื่อเท่ากันต้องมีกติกาตัดสินลำดับ (tie-break) ที่ตัดสินจากข้อมูลของ Job
 * ไม่ขึ้นกับว่า Thread ใดเข้าถึงคิวก่อน - หัวข้อ 7: ห้ามวนลูปเช็กแบบกิน CPU
 * (busy waiting) — Worker ที่ไม่มีงานทำ ต้องถูกพักไว้ ไม่ใช่วนถามซ้ำ ๆ
 *
 * จะออกแบบเป็นคลาสเดียวที่รับนโยบายเข้ามา หรือแยกเป็นสองคลาส
 * หรือใช้โครงสร้างข้อมูลสำเร็จรูปของ Java ก็ได้ ขอให้อธิบายเหตุผลได้ใน Demo
 */
public class ReadyQueue {

    // TODO: เก็บนโยบาย (Config.Policy) และโครงสร้างข้อมูลที่ใช้เก็บงาน
    /**
     * ห่อ Job ไว้พร้อมเลขลำดับที่เข้าคิว เพื่อใช้เรียงแบบ FCFS
     */
    private static class Entry {

        final Job job;
        final long ticket;

        Entry(Job job, long ticket) {
            this.job = job;
            this.ticket = ticket;
        }
    }
    private final Config.Policy policy;     // นโยบายที่ใช้เลือก Job
    private final PriorityBlockingQueue<Entry> queue; // คิวที่ปลอดภัยสำหรับการใช้งานหลาย Thread
    private final AtomicLong ticketCounter = new AtomicLong(0); // ใช้สร้างลำดับสำหรับ FCFS

    public ReadyQueue(Config.Policy policy) {
        // TODO
        this.policy = policy;

        Comparator<Entry> cmp;
        if (policy == Config.Policy.PRIORITY) {
            // priority น้อย = สำคัญกว่า (1 สูงสุด) ถ้าเท่ากันดู sequence ใน CSV
            // ถ้า Priority เท่ากัน ใช้ sequence ของ Job เป็นตัวตัดสิน
            cmp = Comparator
                    .comparingInt((Entry e) -> e.job.priority)
                    .thenComparingInt(e -> e.job.sequence);
        } else {
            // FCFS: ใครเข้าคิวก่อนออกก่อน
            cmp = Comparator.comparingLong((Entry e) -> e.ticket);
        }
        this.queue = new PriorityBlockingQueue<>(16, cmp);
        // throw new UnsupportedOperationException("TODO: ReadyQueue constructor");
    }

    /**
     * ใส่งานเข้าคิว เรียกโดย Scheduler Thread
     */
    public void add(Job job) {
        // TODO
        queue.add(new Entry(job, ticketCounter.getAndIncrement()));
        // throw new UnsupportedOperationException("TODO: ReadyQueue.add");
    }

    /**
     * หยิบงานถัดไปตามนโยบาย เรียกโดย Worker Thread
     *
     * ถ้ายังไม่มีงาน ต้องรอโดยไม่กิน CPU ต้องคิดด้วยว่าจะบอก Worker
     * อย่างไรเมื่อไม่มีงานเหลือแล้วและควรหยุดทำงาน
     */
    public Job take() throws InterruptedException {
        // TODO
        return queue.take().job;
        //throw new UnsupportedOperationException("TODO: ReadyQueue.take");
    }

    /**
     * จำนวนงานที่รออยู่ตอนนี้ ใช้โดย Monitor — ต้องอ่านได้อย่างปลอดภัย
     */
    public int size() {
        // TODO
        return queue.size();
    }
}
