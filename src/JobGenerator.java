import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * ปล่อยงานเข้าสู่ระบบตามเวลา arrivalMs ของแต่ละ Job
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * หน้าที่ (หัวข้อ 3 ของโจทย์):
 *   - รอจนถึงเวลา arrivalMs ของแต่ละงาน แล้วส่งงานต่อไปยัง Scheduler
 *   - บันทึกเวลาที่งานเข้าสู่ระบบ "จริง" ลงใน Job
 *     (อาจไม่ตรงกับ arrivalMs เป๊ะ เพราะ Thread ถูกปลุกช้าได้)
 *   - เรียก logger.jobArrived(job) ทุกครั้งที่ปล่อยงาน
 *
 * ข้อควรคิด:
 *   - รายการงานที่ได้จาก WorkloadLoader เรียงตามลำดับในไฟล์ ไม่ได้เรียงตามเวลา
 *   - เมื่อปล่อยงานครบทุกชิ้นแล้ว ต้องมีวิธีบอกระบบว่า "จะไม่มีงานเข้ามาอีก"
 *     ดู TODO เรื่องการปิดระบบใน Main
 */
public class JobGenerator extends Thread {

    // TODO: เก็บรายการงาน, ช่องทางส่งงานไปยัง Scheduler และ logger
    //
    // หมายเหตุ: constructor ด้านล่างยังไม่มี parameter สำหรับ "ช่องทางส่งงาน"
    // เพราะเป็นสิ่งที่กลุ่มต้องออกแบบเอง (หัวข้อ 2 ห้ามให้ JobGenerator
    // ใส่งานลง ReadyQueue โดยตรง ต้องผ่าน Scheduler เสมอ)
    // ให้เพิ่ม parameter เข้าไปตามที่ออกแบบ เช่น BlockingQueue<Job>
    // หรือคลาสของกลุ่มเอง — เพิ่ม parameter ได้ แต่อย่าเปลี่ยนชื่อคลาส
     private final List<Job> jobs;
    private final BlockingQueue<Job> outgoing;
    private final ProjectLogger logger;

    public JobGenerator(List<Job> jobs, BlockingQueue<Job> outgoing, ProjectLogger logger) {
         super("generator");
        // TODO
        this.jobs = new ArrayList<>(jobs);   // ทำสำเนา ไม่แก้ลิสต์ต้นฉบับ
        // เรียงตามเวลาเข้าระบบ ถ้าเท่ากันดูลำดับใน CSV
        this.jobs.sort(Comparator.comparingLong((Job j) -> j.arrivalMs)
                                 .thenComparingInt(j -> j.sequence));
        this.outgoing = outgoing;
        this.logger = logger;
    }

    @Override
    public void run() {
        // TODO: วนปล่อยงานตามเวลา แล้วแจ้งเมื่อปล่อยครบ
         try {
            for (Job job : jobs) {
                long waitMs = job.arrivalMs - logger.now();
                if (waitMs > 0) {
                    Thread.sleep(waitMs);        // พักจนถึงเวลาเข้าระบบ
                }
                job.arrivedAtMs = logger.now();  // เวลาเข้าจริง
                logger.jobArrived(job);
                outgoing.put(job);               // ส่งให้ Scheduler ไม่ใส่ ReadyQueue ตรง ๆ
            }
            // TODO ขั้นที่ 5: ไม่ต้องแจ้ง เพราะ Main รอ latch
        } catch (InterruptedException e) {
            // ถูกสั่งหยุด
        }
    }
}
