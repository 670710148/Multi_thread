
import java.util.ArrayList;
import java.util.List;

public class WorkerInterruptTest {

    public static void main(String[] args) throws Exception {
        ProjectLogger logger = new ProjectLogger();

        // 3 งานแย่ง PRINTER (permit=1) ทุกงานทำงานหลัก 100 ms แล้วถือ PRINTER 1000 ms
        List<Job> jobs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            jobs.add(new Job("T0" + (i + 1), 0, 1, 100, ResourceType.PRINTER, 1000, i));
        }

        ResourceManager resources = new ResourceManager(1, 1);
        ReadyQueue queue = new ReadyQueue(Config.Policy.FCFS);
        Statistics stats = new Statistics(jobs.size());

        List<Worker> workers = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Worker w = new Worker("worker-" + i, queue, resources, stats, logger);
            workers.add(w);
            w.start();
        }
        for (Job j : jobs) {
            j.arrivedAtMs = logger.now();
            queue.add(j);
        }

        // รอให้ทุก Worker ทำงานหลักเสร็จ: 1 ตัวถือ PRINTER, อีก 2 ตัวรอ
        Thread.sleep(500);
        System.out.println("ก่อน interrupt : " + resources.status());

        // interrupt Worker ที่กำลังรอ PRINTER (ทดสอบทั้งกรณีรอและกรณีถือ)
        workers.get(1).interrupt();   // น่าจะกำลังรอ
        workers.get(0).interrupt();   // น่าจะกำลังถือ

        // ต้องไม่ค้าง: ถ้า latch ไม่ครบ บรรทัดนี้จะรอตลอดไป
        stats.awaitAllDone();
        System.out.println("awaitAllDone ผ่าน (ไม่ค้าง)");

        for (Worker w : workers) {
            w.interrupt();
        }
        for (Worker w : workers) {
            w.join();
        }

        System.out.println("หลังจบ        : " + resources.status());
        System.out.println("completed=" + stats.completedCount() + "/" + jobs.size());
    }
}
