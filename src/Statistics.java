
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

/**
 * รวบรวมและคำนวณค่าที่ใช้วัดผลของการรันหนึ่งครั้ง
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ข้อกำหนดจากโจทย์ที่เกี่ยวกับคลาสนี้ (หัวข้อ 8): - Waiting Time, Turnaround
 * Time, Throughput, Resource Wait Time - ต้องถูกอัปเดตจากหลาย Worker
 * พร้อมกันได้อย่างปลอดภัย - ผลต้องสอดคล้องกับสมการตรวจสอบ: Turnaround = Waiting
 * + workMs + Resource Wait + resourceMs
 * ใช้สมการนี้ตรวจงานทีละชิ้นได้ว่าค่าไหนคำนวณผิด
 *
 * ข้อควรระวัง: ค่าเฉลี่ยของ Resource Wait ให้คิดเฉพาะงานที่ใช้ resource
 * ส่วนงานที่ resource = NONE ให้ถือว่า Resource Wait เป็น 0
 */
public class Statistics {

    // TODO: เก็บข้อมูลของงานที่เสร็จแล้ว หรือเก็บผลรวมไว้คำนวณทีหลัง
    private final List<Job> completed = new ArrayList<>();
    private int running = 0;   // งานที่ Worker กำลังทำ ป้องกันด้วย lock ตัวเดียวกับ completed
    private final CountDownLatch allDone;

    public Statistics(int totalJobs) {
        this.allDone = new CountDownLatch(totalJobs);
    }

    /** main เรียกเพื่อรอจนงานเสร็จครบ */
    public void awaitAllDone() throws InterruptedException {
        allDone.await();
    }

    public void jobStarted() {
        synchronized (completed) {
            running++;
        }
    }

    /**
     * บันทึกว่างานชิ้นหนึ่งเสร็จแล้ว เรียกโดย Worker หลายตัวพร้อมกันได้
     */
    public void recordCompletion(Job job) {
        // TODO
        synchronized (completed) {
            completed.add(job);
            running--;          // ย้ายจาก running ไป completed ในจังหวะเดียว
        }
        allDone.countDown();  // ลดค่า CountDownLatch เมื่อทำงานเสร็จ    
    }

        /** งานถูกยกเลิกกลางคัน (เช่น Worker ถูก interrupt) ลด running และนับให้ latch ไม่ค้าง */
    public void jobAborted() {
        synchronized (completed) {
            running--;
        }
        allDone.countDown();
    }

    /**
     * จำนวนงานที่เสร็จแล้ว ใช้โดย Monitor และใช้ตรวจว่างานครบหรือยัง
     */
    public int completedCount() {
        // TODO
        synchronized (completed) {
            return completed.size();
        }
    }

    /**
     * snapshot {running, completed} อ่านพร้อมกันภายใต้ lock เดียว
     */
    public int[] snapshot() {
        synchronized (completed) {
            return new int[]{running, completed.size()};
        }
    }

    /**
     * พิมพ์ตารางสรุปผลตอนจบโปรแกรม อย่างน้อยต้องมี avg Waiting Time, avg
     * Turnaround Time, Throughput และ avg Resource Wait Time
     *
     * ตามหัวข้อ 14 ให้รายงานเวลาเป็นจำนวนเต็มหน่วย ms และ Throughput อย่างน้อย
     * 2 ตำแหน่งทศนิยม
     */
    public void printSummary(List<Job> allJobs, long makespanMs) {
        // TODO
        List<Job> done;
        synchronized (completed) {
            done = new ArrayList<>(completed);   // สำเนา ไม่ถือ lock ตอนคำนวณ
        }

        long sumWaiting = 0, sumTurnaround = 0;
        long sumResWait = 0;
        int resJobs = 0;
        long maxDiff = 0;

        for (Job j : done) {
            long waiting = j.startedAtMs - j.arrivedAtMs;
            long turnaround = j.finishedAtMs - j.arrivedAtMs;
            sumWaiting += waiting;
            sumTurnaround += turnaround;

            if (j.resource != ResourceType.NONE) {
                sumResWait += j.resourceWaitTotalMs;
                resJobs++;
            }

            // ตรวจสมการ: Turnaround = Waiting + workMs + ResourceWait + resourceMs
            long expected = waiting + j.workMs + j.resourceWaitTotalMs + j.resourceMs;
            maxDiff = Math.max(maxDiff, Math.abs(turnaround - expected));
        }

        int n = done.size();
        long avgWaiting = n == 0 ? 0 : Math.round((double) sumWaiting / n);
        long avgTurnaround = n == 0 ? 0 : Math.round((double) sumTurnaround / n);
        long avgResWait = resJobs == 0 ? 0 : Math.round((double) sumResWait / resJobs);
        double throughput = makespanMs <= 0 ? 0 : n / (makespanMs / 1000.0);

        System.out.println("========== SUMMARY ==========");
        System.out.println("completed             : " + n + "/" + allJobs.size());
        System.out.println("makespan              : " + makespanMs + " ms");
        System.out.println("avg Waiting Time      : " + avgWaiting + " ms");
        System.out.println("avg Turnaround Time   : " + avgTurnaround + " ms");
        System.out.println("avg Resource Wait     : " + avgResWait + " ms (เฉพาะงานที่ใช้ resource " + resJobs + " งาน)");
        System.out.println(String.format(Locale.US, "Throughput            : %.2f jobs/sec", throughput));
        System.out.println("equation max diff     : " + maxDiff + " ms");
    }
}
