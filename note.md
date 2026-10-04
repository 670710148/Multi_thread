# คู่มืออธิบายโค้ดทั้งระบบ (สำหรับอ่านเตรียม Demo)

517-312 Operating Systems — Mini Job Scheduler & Shared Resource Manager

> ไฟล์นี้ไว้อ่านเองเพื่อเตรียมตอบอาจารย์ ไม่ต้องส่ง (ส่งเฉพาะ README.md, log, สรุป 1 หน้า, AI log)
> ทุกส่วนเขียนตามแบบ: **TODO นี้ทำอะไร -> เราเลือกวิธีอะไร -> เพราะอะไร -> ถ้าอาจารย์ถามต้องตอบว่าอะไร**

---

## สารบัญ

0. ภาพรวมระบบ (วาดบนกระดานได้)
1. ลำดับที่ข้อมูลไหล และ Thread ทั้งหมด
2. อธิบายทีละไฟล์ (Job, ReadyQueue, Scheduler, JobGenerator, ResourceManager, Worker, Statistics, Monitor, Main)
3. ไฟล์ที่อาจารย์ให้และเราไม่ได้แก้
4. ไฟล์ทดสอบเพิ่มเติม
5. ข้อมูลที่หลาย Thread ใช้ร่วมกัน (ตารางสรุป)
6. คำตอบ 4 ข้อของโจทย์ (หัวข้อ 14)
7. คำถามที่อาจารย์อาจถามตอน Demo (หัวข้อ 15) พร้อมคำตอบ
8. คำถามเพิ่มเติมที่น่าจะโดน
9. โจทย์แก้สดที่อาจโดน และวิธีทำ
10. ศัพท์และ API ที่ต้องอธิบายได้
11. สิ่งที่ต้องซ้อมก่อนเข้า Demo

---

## 0. ภาพรวมระบบ

```
                         incoming (LinkedBlockingQueue)
JobGenerator ──────────────────────────────────────────▶ Scheduler
 (ปล่อยงานตาม arrivalMs)                                      │ add()
                                                              ▼
                                                        ReadyQueue
                                                (PriorityBlockingQueue + comparator)
                                                              │ take()  (บล็อกถ้าว่าง)
                                       ┌──────────────┬───────┴───────┐
                                       ▼              ▼               ▼
                                   Worker-1       Worker-2   ...  Worker-N
                                       │  acquire/release
                                       ▼
                               ResourceManager
                         (Semaphore PRINTER / DATABASE, fair)

 Worker ──jobStarted / recordCompletion / jobAborted──▶ Statistics ◀── snapshot() ── Monitor (ทุก ~1000 ms)
                                                             │
                                                             └── CountDownLatch ──▶ Main รอจนงานครบ แล้วสั่งหยุดทุก Thread
```

**แนวคิดหลัก 3 ข้อที่ต้องพูดได้ในประโยคเดียว**

1. **ส่งงานระหว่าง Thread ด้วย BlockingQueue** ไม่มี busy waiting เพราะ Thread ที่ไม่มีงานจะ "หลับ" (WAITING) จน queue มีของ
2. **ของที่หลาย Thread แก้ร่วมกัน** (`running`, `completed`) อยู่ใน `Statistics` ภายใต้ `synchronized` lock เดียวกัน ส่วน resource ใช้ `Semaphore`
3. **การปิดระบบ** ใช้ `CountDownLatch` นับจำนวนงานที่จบ เมื่อครบ Main จึง `interrupt()` + `join()` ทุก Thread (ไม่ใช้ `Thread.stop`, ไม่ใช้ `System.exit` ปิดระบบ, ไม่เดาเวลา)

---

## 1. Thread ทั้งหมดและหน้าที่

| Thread | จำนวน | ทำอะไร | หลับ/รอที่ไหน | จบเมื่อ |
|---|---|---|---|---|
| main | 1 | สร้างทุกอย่าง, start, รอ latch, สั่งหยุด, join, พิมพ์สรุป | `awaitAllDone()` | พิมพ์สรุปเสร็จ |
| generator | 1 | ปล่อยงานเข้า `incoming` ตามเวลา | `Thread.sleep` | ปล่อยครบ (หรือถูก interrupt) |
| scheduler | 1 | ย้ายงานจาก `incoming` ไป `ReadyQueue` | `incoming.take()` | ถูก interrupt |
| worker-1..N | N | ดึงงานจาก ReadyQueue ไปทำจนเสร็จ | `readyQueue.take()`, `sleep`, `Semaphore.acquire` | ถูก interrupt |
| monitor | 1 | รายงานสถานะทุก ~1000 ms | `Thread.sleep(1000)` | ถูก interrupt |

ไม่มี Thread ไหนวนลูปเช็กเงื่อนไขซ้ำโดยไม่พัก (ไม่ busy wait) ทุกการรอใช้ `take()`, `acquire()`, `sleep()` หรือ `await()`

---

## 2. อธิบายทีละไฟล์

### 2.1 `Job.java` — เพิ่มฟิลด์วัดผล

**TODO:** เพิ่มฟิลด์เก็บค่าที่ใช้วัดผลของแต่ละ Job

**โค้ดที่เพิ่ม**

```java
public volatile long arrivedAtMs;            // เวลาเข้าระบบจริง (JobGenerator เขียน)
public volatile long startedAtMs;            // เวลา Worker เริ่มทำ (Worker เขียน)
public volatile long finishedAtMs;           // เวลาทำเสร็จ (Worker เขียน)
public volatile long resourceWaitStartedAtMs;// เวลาเริ่มรอ resource (Worker เขียน)
public volatile long resourceWaitTotalMs;    // เวลารอ resource รวม (Worker เขียน)
```

**ทำไมเลือกแบบนี้**

- โจทย์ให้ตอบ 3 คำถามก่อนเขียน:
  1. **ใช้นาฬิกาตัวไหน:** `logger.now()` ตัวเดียวทั้งโปรเจกต์ (ไม่ใช้ `System.currentTimeMillis`) เพราะเป็นฐานเวลาเดียวกับที่พิมพ์ใน log จึงเอาค่าที่วัดไปเทียบกับ log ได้
  2. **ฟิลด์ไหนเขียนโดย Thread หนึ่ง อ่านโดยอีก Thread:** ทุกฟิลด์ เช่น `arrivedAtMs` เขียนโดย generator แต่ Worker/main อ่าน, `startedAtMs`/`finishedAtMs` เขียนโดย Worker แต่ main อ่านตอนสรุป -> ประกาศ `volatile` เพื่อให้ Thread อื่นเห็นค่าล่าสุดเสมอ (visibility)
  3. **สมการตรวจสอบ:** Turnaround = Waiting + workMs + Resource Wait + resourceMs ใช้ตรวจใน `Statistics.printSummary`
- ใช้ `long` เพราะเป็นมิลลิวินาที
- ไม่ต้อง `synchronized` กับฟิลด์พวกนี้ เพราะแต่ละฟิลด์มี **ผู้เขียนคนเดียว** (ไม่มีสองคนเขียนพร้อมกัน) `volatile` จึงพอ

**ถ้าอาจารย์ถาม "volatile ทำอะไร"**
ตอบ: ทำให้ค่าที่ Thread หนึ่งเขียนถูกมองเห็นโดย Thread อื่นทันที ไม่ถูกแคชไว้ใน register/แคช CPU ของ Thread นั้น แต่ `volatile` **ไม่ช่วย** กับ `x++` (อ่านแล้วเขียน) ซึ่งต้องใช้ lock หรือ Atomic

---

### 2.2 `ReadyQueue.java` — คิวงานพร้อมทำ + นโยบาย FCFS/Priority

**TODO:** เก็บนโยบายและโครงสร้างข้อมูล, `add`, `take`, `size`

**โค้ดหลัก**

```java
private static class Entry { final Job job; final long ticket; ... }

private final PriorityBlockingQueue<Entry> queue;
private final AtomicLong ticketCounter = new AtomicLong(0);

public ReadyQueue(Config.Policy policy) {
    Comparator<Entry> cmp;
    if (policy == Config.Policy.PRIORITY) {
        cmp = Comparator.comparingInt((Entry e) -> e.job.priority)   // เลขน้อย = สำคัญกว่า
                        .thenComparingInt(e -> e.job.sequence);      // เท่ากัน ดูลำดับใน CSV
    } else {
        cmp = Comparator.comparingLong((Entry e) -> e.ticket);       // ใครเข้าคิวก่อนออกก่อน
    }
    this.queue = new PriorityBlockingQueue<>(16, cmp);
}
public void add(Job job)  { queue.add(new Entry(job, ticketCounter.getAndIncrement())); }
public Job take() throws InterruptedException { return queue.take().job; }
public int size()         { return queue.size(); }
```

**ทำไมเลือก `PriorityBlockingQueue` ตัวเดียวสำหรับทั้งสองนโยบาย**

- ได้ **thread-safe + blocking** ในตัว: `take()` ทำให้ Worker หลับเมื่อคิวว่าง ตื่นเมื่อมีงาน โดยไม่ต้องเขียน `wait/notify` เองและไม่ต้องวนลูปรอ
- เปลี่ยนนโยบายแค่เปลี่ยน **comparator** โค้ดส่วนอื่น (Worker, Scheduler) ไม่ต้องรู้ว่าใช้นโยบายไหน -> อธิบายง่าย
- `size()` ของคิวนี้ปลอดภัยต่อการเรียกจาก Monitor

**ทำไมต้องมี `Entry` และ `ticket`**

- `PriorityBlockingQueue` ไม่รับประกัน FIFO เมื่อค่าเปรียบเทียบเท่ากัน ดังนั้น FCFS ต้องมี "เลขลำดับเข้าคิว" ชัดเจน
- `ticket` มาจาก `AtomicLong.getAndIncrement()` -> หลาย Thread เรียกพร้อมกันก็ไม่ซ้ำ
- ห่อด้วย `Entry` เพื่อเก็บ ticket **โดยไม่ต้องแก้ class `Job`**

**กติกา tie-break (ข้อสำคัญที่อาจารย์ถามแน่)**

| นโยบาย | ลำดับการเลือก |
|---|---|
| FCFS | `ticket` น้อยกว่ามาก่อน (คือใครถูก Scheduler ใส่ ReadyQueue ก่อน) |
| PRIORITY | `priority` น้อยกว่ามาก่อน (1 = สำคัญสุด) ถ้าเท่ากันใช้ `sequence` (ลำดับแถวใน CSV) น้อยกว่ามาก่อน |

เหตุผลที่ใช้ `sequence`: เป็นค่าคงที่ ไม่ขึ้นกับจังหวะของ Thread ผลจึง **ทำซ้ำได้ (deterministic)** และตรวจด้วย `jobs_same_priority.csv` ได้ (ทุกงาน priority=2 -> ต้องออกตามลำดับ S01..S06)

**ข้อควรรู้:** เป็น **non-preemptive** งานที่ Worker เริ่มทำแล้วไม่ถูกแย่ง การเลือกเกิดเฉพาะตอน Worker ว่างมาดึงงานใหม่

**ข้อเสียที่ควรบอกเอง:** Priority อาจเกิด starvation (งาน priority ต่ำรอนาน) เพราะเราไม่ได้ทำ aging (เป็นงานเสริม)

**ถ้าถามว่าทำไมไม่ใช้ `LinkedBlockingQueue` สำหรับ FCFS**
ตอบ: ใช้ได้เหมือนกัน แต่เลือกโครงเดียวกันทั้งสองนโยบายเพื่อให้โค้ดสั้นและอธิบายง่าย ต่างกันแค่ comparator

---

### 2.3 `Scheduler.java` — รับงานจาก Generator ใส่ ReadyQueue

**TODO:** เก็บช่องทางรับงาน, ReadyQueue, logger และวนรับงาน

**โค้ดหลัก**

```java
private final BlockingQueue<Job> incoming;
...
public void run() {
    try {
        while (true) {
            Job job = incoming.take();   // ไม่มีงานจะพักรอ ไม่กิน CPU
            readyQueue.add(job);
        }
    } catch (InterruptedException e) {
        // ถูกสั่งหยุด ออกจากลูป Thread จบเอง
    }
}
```

**ทำไมเลือกแบบนี้**

- โจทย์ข้อ 2 **ห้าม** ให้ JobGenerator ใส่ ReadyQueue ตรง ๆ ต้องผ่าน Scheduler -> เลยเพิ่ม parameter `incoming` ใน constructor (โจทย์อนุญาตให้เพิ่ม parameter แต่ห้ามเปลี่ยนชื่อ)
- ช่องทางส่งงานคือ `BlockingQueue<Job>` (ในทางปฏิบัติคือ `LinkedBlockingQueue`) แบบ **producer-consumer**: Generator คือ producer, Scheduler คือ consumer
- การหยุด: ถูก `interrupt()` ตอนติดอยู่ที่ `take()` จะเกิด `InterruptedException` แล้วออกจากลูปเอง ไม่ต้องมีตัวแปร flag

**ถ้าถามว่า Scheduler ทำอะไรเยอะไหม / จำเป็นไหม**
ตอบ: ตอนนี้เป็นตัวส่งต่อเรียบ ๆ แต่ในเชิงโครงสร้างมันแยก "งานที่เข้าระบบ" ออกจาก "งานที่พร้อมทำ" และเป็นจุดที่ใส่ logic เพิ่มได้ในอนาคต เช่น aging หรือ admission control

---

### 2.4 `JobGenerator.java` — ปล่อยงานตามเวลา

**TODO:** เก็บรายการงาน/ช่องทางส่ง/logger และปล่อยงานตาม `arrivalMs`

**โค้ดหลัก**

```java
this.jobs = new ArrayList<>(jobs);                 // ทำสำเนา ไม่แก้ลิสต์ต้นฉบับ
this.jobs.sort(Comparator.comparingLong((Job j) -> j.arrivalMs)
                         .thenComparingInt(j -> j.sequence));
...
for (Job job : jobs) {
    long waitMs = job.arrivalMs - logger.now();
    if (waitMs > 0) Thread.sleep(waitMs);
    job.arrivedAtMs = logger.now();                // เวลาเข้าจริง
    logger.jobArrived(job);
    outgoing.put(job);                             // ส่งให้ Scheduler
}
```

**ทำไมเลือกแบบนี้**

- **เรียงก่อนปล่อย:** `WorkloadLoader` ให้ลิสต์ตามลำดับในไฟล์ ไม่ได้เรียงตามเวลา (โจทย์บอกไว้) ถ้าไม่เรียง งานที่ arrival มาก่อนอาจถูกปล่อยช้า
- **ทำสำเนา:** ไม่ไปแก้ลิสต์ของ Main (ซึ่ง Main ใช้ต่อใน `printSummary`)
- **`sleep(arrivalMs - logger.now())`** เทียบกับ "เวลาปัจจุบันของระบบ" ไม่ใช่ sleep ต่อกันทีละช่วง -> ความคลาดเคลื่อนไม่สะสม (ไม่มี drift)
- **`arrivedAtMs` แยกจาก `arrivalMs`:** `arrivalMs` คือเวลาที่ไฟล์กำหนด, `arrivedAtMs` คือเวลาที่เข้าระบบจริง (Thread อาจตื่นช้ากว่านิดหน่อย) Waiting/Turnaround วัดจากค่าจริง
- **`put()` ลง `incoming`:** เป็น `LinkedBlockingQueue` แบบไม่จำกัดขนาด `put` จึงไม่ค้าง
- **ไม่ผูกกับ id หรือจำนวนงาน** ของไฟล์ตัวอย่าง (โจทย์เตือนว่าวัน Demo อาจใช้ workload อื่น)

**เรื่องการแจ้งว่า "ไม่มีงานเข้าแล้ว":** เราไม่ใช้ poison pill เพราะ Main รู้จำนวนงานทั้งหมด (`jobs.size()`) แล้วนับด้วย latch แทน (ดู 2.7/2.9) จึงไม่ต้องให้ Generator ส่งสัญญาณพิเศษ (คอมเมนต์ "TODO ขั้นที่ 5" ที่เหลือในไฟล์หมายถึงเรื่องนี้ ตัดทิ้งได้)

---

### 2.5 `ResourceManager.java` — Semaphore ของ PRINTER / DATABASE

**TODO:** เก็บ Semaphore, `acquire`, `release`, `status`

**โค้ดหลัก**

```java
this.printer  = new Semaphore(printerPermits, true);   // fair = true
this.database = new Semaphore(databasePermits, true);

public void acquire(ResourceType type) throws InterruptedException {
    Semaphore s = semaphoreOf(type);
    if (s != null) s.acquire();          // NONE -> ไม่ต้องทำอะไร
}
public void release(ResourceType type) { Semaphore s = semaphoreOf(type); if (s != null) s.release(); }
public String status() { return "printer=" + printer.availablePermits() + "/" + printerTotal
                              + " database=" + database.availablePermits() + "/" + databaseTotal; }
```

**ทำไมเลือกแบบนี้**

- **Semaphore** เหมาะกับ "ทรัพยากรที่มีหลายช่อง" (printer=1 ช่อง, database=2 ช่อง) permits = จำนวนที่ใช้พร้อมกันได้สูงสุด ถ้าใช้ `synchronized` จะให้ได้แค่ 1 คนเสมอ
- **`fair = true`**: Thread ที่รอก่อนได้ก่อน (FIFO) ป้องกันงานบางตัวรอเรื่อย ๆ (starvation) และทำให้ผลอธิบายง่าย ต้นทุนคือช้ากว่าเล็กน้อย
- **แยก Semaphore ต่อ resource**: PRINTER กับ DATABASE ไม่ยุ่งกัน
- **`semaphoreOf(type)`** helper: คืน `null` ให้ `NONE` -> `acquire/release` ของงานที่ไม่ใช้ resource จึงเป็น no-op ปลอดภัย
- **`status()`** ใช้ `availablePermits()` ซึ่งเป็นค่าประมาณ ณ ขณะอ่าน เหมาะกับ Monitor (ไม่ต้องแม่นเป๊ะ) รูปแบบ `ว่าง/ทั้งหมด`
- `acquire` โยน `InterruptedException` ออกไปให้ Worker จัดการ (ไม่กลืน) เพื่อให้ Worker รู้ว่าถูกสั่งหยุด

**ไม่เกิด deadlock เพราะ:** 1 Job ใช้ resource ได้แค่ชนิดเดียวและขอแค่ครั้งเดียว ไม่ถือ resource หนึ่งไว้แล้วรออีกอัน จึงไม่มีวงจรการรอ

---

### 2.6 `Worker.java` — วงจรรับงานและ `processJob`

**TODO:** เก็บ dependency, `run()` วนรับงาน, `processJob()` ทำตาม 5 ขั้นของโจทย์

**`run()`**

```java
try {
    while (true) {
        Job job = readyQueue.take();   // คิวว่างจะพักรอ ไม่กิน CPU
        processJob(job);
    }
} catch (InterruptedException e) {
    // ถูกสั่งหยุด ออกจากลูป Thread จบเอง
}
```

**`processJob()` (เวอร์ชันสุดท้าย)**

```java
private void processJob(Job job) throws InterruptedException {
    statistics.jobStarted();                       // running++ (ใต้ lock)
    boolean finished = false;
    try {
        // ขั้น 1: บันทึกเวลาเริ่ม
        logger.jobStarted(job);
        job.startedAtMs = logger.now();

        // ขั้น 2: งานหลัก
        Thread.sleep(job.workMs);
        logger.workFinished(job);

        // ขั้น 3-5: resource
        if (job.resource != ResourceType.NONE) {
            job.resourceWaitStartedAtMs = logger.now();
            logger.resourceWaitStarted(job);

            resources.acquire(job.resource);       // อยู่ "นอก" try ด้านใน
            long waited = logger.now() - job.resourceWaitStartedAtMs;
            job.resourceWaitTotalMs = waited;
            logger.resourceAcquired(job, waited);

            try {
                Thread.sleep(job.resourceMs);      // ถือครอง
            } finally {
                resources.release(job.resource);   // คืนเสมอ
                logger.resourceReleased(job);
            }
        }

        job.finishedAtMs = logger.now();
        logger.jobCompleted(job);
        statistics.recordCompletion(job);          // completed++, running--, latch.countDown()
        finished = true;
    } finally {
        if (!finished) statistics.jobAborted();    // ถูก interrupt/exception กลางคัน
    }
}
```

**ทำไมเลือกแบบนี้ (แยกเป็นข้อ)**

1. **ลำดับ 2 -> 3 ห้ามสลับ** (โจทย์บังคับ): ทำงานหลักก่อนแล้วค่อยขอ resource ถ้าสลับ ผลของทุกกลุ่มเทียบกันไม่ได้ และ resource จะถูกถือนานโดยไม่จำเป็น
2. **`acquire` อยู่นอก `try/finally` ด้านใน:** ถ้าถูก interrupt ตอนรอ `acquire` แปลว่า **ยังไม่ได้ permit** ถ้า release ใน finally ที่ครอบ acquire ด้วย จะ release permit ที่ไม่เคยได้ -> จำนวน permit เพิ่มเกินจริง (บั๊กร้ายแรง) จึงให้ `try` เริ่มหลัง acquire สำเร็จเท่านั้น
3. **`release` อยู่ใน `finally`:** ไม่ว่าจะ sleep เสร็จปกติ ถูก interrupt หรือเกิด exception ระหว่างถือ permit คืนแน่นอน -> ระบบไม่แขวน
4. **ไม่ใช้ `Thread.stop`** (deprecated และไม่ปลอดภัย) ใช้ interrupt แบบ cooperative เท่านั้น
5. **`jobAborted()` + flag `finished`:** ตอนแรกเรามองข้ามกรณีนี้ ถ้า Worker ถูก interrupt กลางงาน `recordCompletion` ไม่ถูกเรียก -> `running` ค้างเกิน และ latch ไม่ถูกนับ -> Main รอ `awaitAllDone()` ตลอดไป (ค้าง) จึงเพิ่ม `finally` ที่เรียก `jobAborted()` (ลด running และนับ latch) ตรวจแล้วด้วย `WorkerInterruptTest`
6. **เวลารอ resource** วัดด้วย `logger.now()` ก่อนและหลัง `acquire` เก็บไว้ที่ `job.resourceWaitTotalMs` เพื่อใช้ตรวจสมการ
7. **`running++` ทำก่อนเริ่มงาน:** เพื่อให้ Monitor เห็นงานที่กำลังทำถูกต้อง

**Thread.State ของ Worker ในแต่ละช่วง**

| ช่วง | Thread.State |
|---|---|
| รอ `readyQueue.take()` | WAITING |
| `Thread.sleep(workMs)` | TIMED_WAITING |
| รอ `Semaphore.acquire()` | WAITING |
| `Thread.sleep(resourceMs)` | TIMED_WAITING |

(`acquire()` ไม่มี timeout จึงเป็น WAITING ส่วน `sleep` มีเวลากำกับจึงเป็น TIMED_WAITING และเมื่อรอ Semaphore Thread **ไม่กิน CPU**)

---

### 2.7 `Statistics.java` — เก็บผลและตัวนับที่ปลอดภัย

**TODO:** เก็บข้อมูลงานที่เสร็จ, ตัวนับ running, ตัวบอกว่าเสร็จครบ, สรุปผล

**โค้ดหลัก**

```java
private final List<Job> completed = new ArrayList<>();
private int running = 0;                         // ใต้ lock เดียวกับ completed
private final CountDownLatch allDone;            // นับถอยหลังจาก totalJobs

public Statistics(int totalJobs) { this.allDone = new CountDownLatch(totalJobs); }
public void awaitAllDone() throws InterruptedException { allDone.await(); }

public void jobStarted()  { synchronized (completed) { running++; } }

public void recordCompletion(Job job) {
    synchronized (completed) { completed.add(job); running--; }   // เปลี่ยนสถานะในจังหวะเดียว
    allDone.countDown();
}
public void jobAborted() {
    synchronized (completed) { running--; }
    allDone.countDown();
}
public int[] snapshot() {
    synchronized (completed) { return new int[]{running, completed.size()}; }
}
```

**ทำไมเลือกแบบนี้**

- **`synchronized` lock ตัวเดียว (`completed`) คุมทั้ง `running` และ `completed`:** ทำให้ `recordCompletion` ย้ายงานจาก running ไป completed **ในจังหวะเดียว** และ `snapshot()` อ่านทั้งคู่ภายใต้ lock เดียวกัน Monitor จึงไม่มีทางเห็นภาพเพี้ยน เช่น งานหายไปจากทั้ง running และ completed ชั่วขณะ
- **ทำไมไม่ใช้ `AtomicInteger` แยกกัน:** แต่ละตัวปลอดภัย แต่ **อ่านสองตัวพร้อมกันไม่เป็น snapshot เดียว** (อ่าน running แล้วอีกขณะ completed เปลี่ยน)
- **ถ้าเอา `synchronized` ออก:** `running++` เป็น 3 ขั้น (อ่าน-บวก-เขียน) สอง Worker อ่านค่า 2 พร้อมกันแล้วต่างเขียน 3 -> ควรเป็น 4 (lost update) ผลคือ `running` เพี้ยน อาจติดลบหรือค้างไม่เป็น 0 และ `ArrayList.add` พร้อมกันอาจทำให้ข้อมูลหาย/ArrayIndexOutOfBounds
- **`CountDownLatch`:** ตั้งค่าเริ่มต้น = จำนวนงานทั้งหมด ทุกงานที่จบ (หรือ abort) `countDown()` หนึ่งครั้ง Main `await()` จนเป็น 0 จึงรู้ว่า "ไม่มีงานเหลือ" โดยไม่เดาเวลา และ **ไม่ทำให้ Main busy wait**
- **`countDown()` อยู่นอก synchronized:** latch ปลอดภัยต่อ Thread อยู่แล้ว ไม่ต้องถือ lock นานโดยไม่จำเป็น
- **`printSummary` ทำสำเนา list ใต้ lock แล้วคำนวณนอก lock** เพื่อไม่ถือ lock ตอนคำนวณนาน

**สูตรที่ใช้ใน `printSummary`**

| ค่า | สูตร | หมายเหตุ |
|---|---|---|
| Waiting | `startedAtMs − arrivedAtMs` | ค่าเฉลี่ยทุกงานที่เสร็จ |
| Turnaround | `finishedAtMs − arrivedAtMs` | ค่าเฉลี่ยทุกงานที่เสร็จ |
| Resource Wait | `resourceWaitTotalMs` | **เฉลี่ยเฉพาะงานที่ใช้ resource** (งาน NONE ไม่มีการรอ ถ้ารวมจะดึงค่าต่ำลงเกินจริง) |
| Throughput | `completed / (makespan วินาที)` | makespan = เวลาที่งานสุดท้ายจบ (`logger.now()` หลัง latch) |
| ตรวจสมการ | `Turnaround − (Waiting + workMs + RW + resourceMs)` | พิมพ์ค่าต่างสูงสุดเป็น `equation max diff` (เราได้ 1–13 ms เพราะ `Thread.sleep` ไม่เป๊ะ) |

---

### 2.8 `Monitor.java` — รายงานสถานะทุก ~1 วินาที

**TODO:** เก็บสิ่งที่ต้องอ่านสถานะ และวนรายงานจนถูกสั่งหยุด

**โค้ดหลัก**

```java
try {
    while (true) {
        Thread.sleep(1000);
        int[] snap = statistics.snapshot();      // {running, completed}
        logger.monitor(readyQueue.size(), snap[0], snap[1], resources.status());
    }
} catch (InterruptedException e) { /* ถูกสั่งหยุด */ }
```

**ทำไมเลือกแบบนี้**

- `sleep(1000)` พักระหว่างรอบ **ไม่กิน CPU** (โจทย์ไม่ต้องการ 1000 ms เป๊ะ)
- ตัวนับ `running` อยู่ที่ **`Statistics`** (ไม่ได้อยู่ใน Monitor หรือ Worker) เพราะ Worker เป็นคนเพิ่ม/ลด และ Monitor เป็นคนอ่าน ต้องมีที่กลางที่ป้องกันด้วย lock จึงเพิ่ม `Statistics` เป็น parameter ใน constructor (โจทย์ใบ้ไว้ว่าให้เพิ่ม parameter เอง)
- Monitor ใช้ `snapshot()` อ่าน running+completed พร้อมกันจาก lock เดียว ส่วน `ready` มาจาก `readyQueue.size()` และ resource จาก `resources.status()`
- Monitor เป็นแค่ผู้อ่าน ไม่ได้แก้ข้อมูลของใคร
- หยุดด้วย interrupt ตอนอยู่ใน `sleep` -> `InterruptedException` -> ออกลูป

**ข้อสังเกต:** `ready` กับ `running+completed` ไม่ได้อ่านใน lock เดียวกัน (คนละ object) ภาพรวมจึงประมาณได้ แต่ **ตัวนับที่ Worker หลายตัวแก้ (running/completed) เป็น snapshot ที่สอดคล้องกัน** ซึ่งเป็นสิ่งที่โจทย์เน้น

---

### 2.9 `Main.java` — ประกอบระบบและปิดระบบ

**TODO ที่เขียน**

| TODO | สิ่งที่ทำ |
|---|---|
| สร้าง ResourceManager | `new ResourceManager(config.printerPermits, config.databasePermits)` |
| สร้าง ReadyQueue | `new ReadyQueue(config.policy)` |
| สร้าง Statistics | `new Statistics(jobs.size())` (ส่งจำนวนงานให้ latch) |
| ช่องทาง Generator→Scheduler | `new LinkedBlockingQueue<Job>()` |
| สร้างและ start Worker | วนลูป `config.workers` ตัว ตั้งชื่อ `worker-1..N` เก็บใน `ArrayList` |
| สร้างและ start Scheduler/Monitor/JobGenerator | ตามลำดับด้านล่าง |
| รอให้งานเสร็จ | `statistics.awaitAllDone()` |
| หยุดและ join | interrupt ทุก Thread แล้ว join ทุกตัว |
| สรุปผล | `makespanMs = logger.now()` แล้ว `printSummary` + `logger.systemStop` |

**ลำดับ start และเหตุผล (โจทย์ให้อธิบายใน Demo)**

Worker -> Scheduler -> Monitor -> JobGenerator (Generator ท้ายสุด)

- เริ่มตัวที่ "รอรับ" ก่อนตัวที่ "ส่ง" เพื่อให้ผู้บริโภคพร้อมก่อนมีงานเข้า และเวลาใน log เริ่มนับสอดคล้องกัน
- เนื่องจากใช้ BlockingQueue ถ้าลำดับสลับ ระบบ **ยังทำงานถูก** (งานจะรอใน queue) ลำดับนี้จึงเกี่ยวกับความเรียบร้อยของ log/ความเป็นธรรมของเวลา ไม่ใช่ความถูกต้อง

**การปิดระบบ (หัวข้อที่ยากที่สุดตามโจทย์)**

```
1. ทุกงานที่จบหรือ abort -> latch.countDown()
2. main: statistics.awaitAllDone()     <- ตื่นเมื่อครบ ไม่ polling
3. makespanMs = logger.now()           <- เวลาที่งานสุดท้ายเพิ่งเสร็จ (จับก่อนปิด Thread)
4. interrupt() generator, scheduler, monitor, workers ทั้งหมด
5. join() ทุกตัว                       <- ยืนยันว่าหยุดจริงก่อนพิมพ์สรุป
6. printSummary + systemStop           <- JVM จบเองเพราะไม่เหลือ non-daemon thread
```

ตรวจกับ "อาการผิดที่ต้องไม่เกิด" ของโจทย์:

| อาการผิด | เราป้องกันอย่างไร |
|---|---|
| main จบแต่ JVM ไม่ปิด (มี Thread ค้าง) | interrupt + join ทุก Thread |
| Worker หยุดก่อนงานสุดท้ายเสร็จ | ไม่หยุดจนกว่า latch จะครบ (ครบเมื่องานสุดท้ายจบ) |
| permit ค้างเพราะ interrupt ระหว่างถือ | `release` อยู่ใน `finally` |

**`System.exit(1)`** ใน Main ใช้เฉพาะ **ก่อน start Thread** เมื่อ argument/ไฟล์ผิด (ตรงโจทย์: แสดง Usage แล้วจบก่อนเริ่ม Thread) ไม่ได้ใช้ปิดระบบปกติ

**ถ้าเพิ่ม Worker ระหว่างทำงาน วิธีปิดระบบยังถูกไหม**
ยังถูก เพราะ latch นับ **จำนวนงาน** ไม่ใช่จำนวน Worker แต่ Worker ใหม่ต้องถูกเก็บในลิสต์ `workers` เพื่อให้ถูก `interrupt()`/`join()` ด้วย และต้องไม่เพิ่มหลังเริ่มปิดระบบ (อีกแบบที่ **จะพัง** คือ poison pill ที่ส่งเท่าจำนวน Worker ตอนเริ่ม เพราะ Worker ใหม่จะไม่มีเม็ดยาพิษให้กิน)

---

## 3. ไฟล์ที่อาจารย์ให้และเราไม่ได้แก้

| ไฟล์ | หน้าที่ |
|---|---|
| `ProjectLogger.java` | พิมพ์ log รูปแบบมาตรฐาน และ `now()` = นาฬิกากลางที่นับ ms จากตอนสร้าง |
| `Config.java` | แปลง argument 5 ตัวเป็น config และมี `USAGE` (ผิดจะโยน exception ให้ Main แสดง Usage) |
| `WorkloadLoader.java` | อ่านและตรวจ CSV |
| `WorkloadFormatException.java` | exception เมื่อ CSV ผิดรูปแบบ |
| `ResourceType.java` | enum `NONE`, `PRINTER`, `DATABASE` |

---

## 4. ไฟล์ทดสอบเพิ่มเติม (ที่เราเขียนเอง)

| ไฟล์ | ทดสอบอะไร | ผล |
|---|---|---|
| `InterruptTest.java` | `ResourceManager` เมื่อ Thread ที่ถือ/รอ permit ถูก interrupt | permit ไม่หาย ไม่เกิน |
| `WorkerInterruptTest.java` | Worker/ReadyQueue/Statistics จริง 3 งานแย่ง PRINTER=1 แล้ว interrupt Worker | ไม่ค้าง, หลังจบ `printer=1/1`, completed=1/3, ไม่มี `RESOURCE_RELEASED` ของงานที่ไม่ได้ permit |

หมายเหตุ: ในการทดสอบ คนที่ได้ PRINTER คือ worker-3 (T03) ไม่ใช่ worker-1 ดังนั้นสิ่งที่ครอบคลุมจริง ๆ คือ "interrupt ระหว่างรอ" (worker-1, worker-2) ส่วน "interrupt ระหว่างถือ" ครอบด้วย `InterruptTest` และ `finally`

---

## 5. ข้อมูลที่หลาย Thread ใช้ร่วมกัน

| ข้อมูล | ใครเขียน | ใครอ่าน | ป้องกันด้วย |
|---|---|---|---|
| `incoming` (queue) | Generator | Scheduler | `LinkedBlockingQueue` (thread-safe) |
| `ReadyQueue` | Scheduler | Worker (หลายตัว), Monitor (`size`) | `PriorityBlockingQueue` |
| `ticketCounter` | Scheduler (ผ่าน `add`) | - | `AtomicLong` |
| `running` | Worker (หลายตัว) | Monitor | `synchronized(completed)` |
| `completed` (list) | Worker (หลายตัว) | Monitor, Main | `synchronized(completed)` |
| Semaphore PRINTER/DATABASE | Worker | Monitor (`availablePermits`) | `Semaphore` (thread-safe) |
| ฟิลด์เวลาใน `Job` | Generator / Worker | Worker / Main | `volatile` (ผู้เขียนคนเดียวต่อฟิลด์) |
| ตัวนับถอยหลัง | Worker | Main | `CountDownLatch` |
| `logger` (พิมพ์ log) | ทุก Thread | - | ของอาจารย์ (ปลอดภัยต่อ Thread ในตัว) |

---

## 6. คำตอบ 4 ข้อของโจทย์ (หัวข้อ 14)

> **ต้องเขียนใหม่ด้วยคำของตัวเอง** และเปิดไฟล์ log ของแถว 1 และ 2 ไปหาลำดับ `JOB_STARTED` จริงมาใส่ ข้างล่างเป็นเนื้อหาให้เข้าใจ ไม่ใช่ให้คัดลอกส่ง

ตัวเลขที่ใช้ (จากการรันจริง)

| # | Workload / Policy | Workers | Printer | DB | avg WT | avg TAT | Throughput | avg RW |
|---|---|---|---|---|---|---|---|---|
| 1 | standard / FCFS | 3 | 1 | 2 | 2961 | 5460 | 1.15 | 79 |
| 2 | standard / Priority | 3 | 1 | 2 | 2324 | 4940 | 1.09 | 247 |
| 3 | standard / Priority | 1 | 1 | 2 | 8526 | 10969 | 0.41 | 1 |
| 4 | standard / Priority | 5 | 1 | 2 | 1131 | 4070 | 1.30 | 708 |
| 5 | printer / Priority | 3 | 1 | 2 | 1182 | 4299 | 0.79 | 1637 |
| 6 | printer / Priority | 3 | 2 | 2 | 810 | 2625 | 1.39 | 336 |
| 7 | standard / Priority (repeat) | 3 | 1 | 2 | 2338 | 4954 | 1.08 | 248 |

### ข้อ 1: เพิ่ม Worker จาก 3 เป็น 5 Throughput เพิ่มตามสัดส่วนไหม และ avg Resource Wait เปลี่ยนอย่างไร เพราะอะไร

**ไม่เพิ่มตามสัดส่วน** Worker เพิ่ม 67% (3 -> 5) แต่ Throughput เพิ่มจาก 1.09 เป็น 1.30 หรือประมาณ 19% (และจาก 1 เป็น 3 Worker, 0.41 -> 1.09 เพิ่ม 2.7 เท่า จาก Worker 3 เท่า ซึ่งใกล้สัดส่วนกว่า) ขณะที่ **avg Resource Wait เพิ่มจาก 247 เป็น 708 ms**

เหตุผล: งานส่วนที่ใช้ CPU-like (`workMs`) ขนานกันได้ตาม Worker แต่ส่วนที่ต้องใช้ **PRINTER มี permit เดียว** ต้องทำทีละงาน (serial) ยิ่งมี Worker มาก ก็ยิ่งมีหลายตัวทำ `workMs` เสร็จแล้วมารอ PRINTER พร้อมกัน เวลารอ resource เลยเพิ่ม ส่วนที่ขนานกันได้มีเพดาน (ตามกฎของ Amdahl) ถึงจุดหนึ่งการเพิ่ม Worker แทบไม่ช่วย เพราะคอขวดย้ายไปที่ PRINTER (และตัวงานที่ยาวที่สุด)

### ข้อ 2: FCFS กับ Priority ทำให้กลุ่ม Job ใดได้/เสียประโยชน์ และทำไม Priority อาจมี Throughput ต่ำกว่า FCFS ทั้งที่ avg WT ดีกว่า

- **ได้ประโยชน์จาก Priority:** J07–J10 (priority 1,1,1,2) ซึ่งเข้าระบบทีหลัง (1000–1600 ms) แต่ถูกดึงข้ามงานเก่า priority 5 ที่รออยู่ใน ReadyQueue (J04, J05, J06) จึงรอสั้นลงมาก
- **เสียประโยชน์:** J04, J05, J06 (priority 5,5,4) ต้องรอนานขึ้นเพราะถูกแซง ส่วน FCFS ให้ตามลำดับมาถึง งานเข้าก่อนเริ่มก่อนเสมอ
- ให้ **เปิด log แถว 1 และ 2 แล้วอ้างลำดับ `JOB_STARTED` จริง** (คาดว่า FCFS เริ่มตาม J01..J10 ส่วน Priority จะเห็น J07/J08/J09 ถูกเริ่มก่อน J04/J05/J06 หลังจาก Worker ว่างตอน ~2500 ms ให้ตรวจกับ log ของคุณเอง)
- avg WT ของ Priority ดีกว่า (2324 vs 2961) เพราะงานสั้นและสำคัญถูกทำก่อน คนจำนวนมากรอสั้น ค่าเฉลี่ยจึงลด
- **Throughput ต่ำกว่า (1.09 vs 1.15 ≈ makespan 9.17 s vs 8.70 s)** เพราะ Throughput วัดจาก **เวลาจบของงานสุดท้าย** ไม่ใช่เวลารอเฉลี่ย Priority ดันงานยาว (workMs ~2400–2500 + resource) ไปไว้ท้าย ทำให้ช่วงท้ายมีงานยาวเหลือและ Worker ว่างไม่เต็ม อีกทั้งงานที่ใช้ PRINTER/DATABASE ถูกทำมารวมกลุ่มในช่วงเดียวกัน แย่ง resource กัน (avg RW 247 เทียบกับ 79) จึงจบช้ากว่า

### ข้อ 3: เพิ่ม Printer permit จาก 1 เป็น 2 avg Resource Wait และ Throughput เปลี่ยนอย่างไร และคอขวดย้ายไปไหน

- avg Resource Wait **ลดมาก** 1637 -> 336 ms (ลดประมาณ 80%)
- Throughput **เพิ่ม** 0.79 -> 1.39 (ราว 76%) และ avg WT/TAT ลดตาม (1182 -> 810, 4299 -> 2625)
- เหตุผล: `jobs_printer.csv` ทุกงานใช้ PRINTER ถือนาน 1200 ms ตอน permit=1 ทำได้ทีละงาน งานที่เหลือต่อคิวรอ พอเป็น 2 ทำพร้อมกันได้ 2 งาน
- **คอขวดย้ายจาก "จำนวน permit ของ PRINTER" ไปเป็น "จำนวน Worker (3 ตัว)"** และบางส่วนยังเป็น PRINTER เพราะ Worker มี 3 แต่ permit มี 2 -> ยังมีบางช่วงต้องรอ (RW ยังไม่เป็น 0) ถ้าอยากให้ Worker เป็นคอขวดเต็มที่ ต้องเพิ่ม permit ให้เท่า Worker

### ข้อ 4: เปรียบเทียบแถว 2 กับ 7 ทำไมค่าที่วัดต่างกันเล็กน้อยทั้งที่ค่าเดิม

ค่าต่างกันแค่ระดับ ms (WT 2324 vs 2338, TAT 4940 vs 4954, Throughput 1.09 vs 1.08, RW 247 vs 248) เพราะ

1. `Thread.sleep(n)` รับประกันแค่ "อย่างน้อย n ms" ตื่นเมื่อไรขึ้นกับ OS scheduler และ timer resolution
2. ลำดับที่ OS สลับ Thread ไม่เหมือนเดิมทุกรอบ เช่น Worker ตัวไหนชนะแย่ง `take()` ก่อน หรือ Semaphore (fair) ให้ใครก่อนเมื่อมาถึงใกล้กันมาก
3. ปัจจัยของเครื่อง เช่น JIT warm-up, GC, โปรแกรมอื่นที่รันอยู่ ทำให้การเรียก `logger.now()` ห่างกันไม่เท่ากัน
4. ความคลาดเคลื่อนเล็ก ๆ ของทุกงานสะสมเป็นเวลารวมที่ต่างกัน

**กติกาและแนวโน้มคงเดิม:** ลำดับ START ตาม priority ยังเหมือนเดิม และสมการ Turnaround ยังลงตัวภายในไม่กี่ ms (ให้เปิด log เทียบ timestamp ของบางบรรทัดจริงประกอบ ตามที่โจทย์ขอ)

---

## 7. คำถาม Demo ตามหัวข้อ 15 ของโจทย์ พร้อมคำตอบ

### 7.1 เปลี่ยนจำนวน Worker หรือ permit ของ Semaphore แล้วระบบจะเปลี่ยนอย่างไร

- **เพิ่ม Worker:** จำนวนงานที่ทำพร้อมกันมากขึ้น avg Waiting ลดและ Throughput เพิ่ม (แต่ไม่เป็นสัดส่วน) ถึงจุดหนึ่งคอขวดย้ายไป resource ทำให้ avg Resource Wait เพิ่ม (เทียบแถว 2 กับ 4)
- **Worker = 1:** งานทำทีละงาน Waiting สูงมาก (8526 ms) Resource Wait แทบเป็น 0 (1 ms) เพราะไม่มีใครแย่งใคร
- **เพิ่ม permit:** งานที่ต้องใช้ resource นั้นรอสั้นลง และอนุญาตทำพร้อมกันได้มากขึ้น (database=2 ต้องเห็นสูงสุด 2 งานพร้อมกัน)
- **permit มากกว่าจำนวน Worker:** ไม่มีประโยชน์เพิ่ม เพราะจำนวนผู้ใช้พร้อมกันถูกจำกัดโดย Worker

### 7.2 ชี้ข้อมูลร่วมที่หลาย Thread แก้ได้ เช่น `running` และถ้าเอาการป้องกันออกจะเกิดอะไร

- `running` อยู่ใน `Statistics` ถูก Worker หลายตัว `++`/`--` พร้อมกัน
- ถ้าเอา `synchronized` ออก: `running++` ไม่ใช่ atomic (อ่าน-บวก-เขียน) ทำให้ **lost update** ค่าเพี้ยน อาจไม่กลับเป็น 0 หรือติดลบ Monitor รายงานผิด
- `completed.add` พร้อมกันบน `ArrayList` ที่ไม่ thread-safe อาจทำให้ข้อมูลหายหรือ exception
- ตอบเพิ่มได้ว่า `snapshot()` ต้องอยู่ใต้ lock เดียวกัน ไม่งั้นเห็นภาพที่ running กับ completed ไม่สอดคล้องกัน

### 7.3 อธิบาย FCFS / Priority และ tie-break จากโค้ดจริง

- เปิด `ReadyQueue` ชี้ comparator: FCFS = `ticket` (เลขเข้าคิว จาก `AtomicLong`), Priority = `priority` น้อยก่อน แล้ว `sequence` (ลำดับใน CSV)
- เหตุผลที่ใช้ `sequence`: คงที่ ทำซ้ำได้ ตรวจด้วย `jobs_same_priority.csv`
- ยืนยันว่า non-preemptive

### 7.4 อธิบายวิธีปิดระบบ และถ้าเพิ่ม Worker ระหว่างทำงานยังใช้ได้ไหม

ดูหัวข้อ 2.9 สรุป: `CountDownLatch` นับงาน -> main `await` -> interrupt + join ทุก Thread ใช้ได้ถ้าเพิ่ม Worker เพราะนับที่งาน ต้องแค่เก็บ Worker ใหม่ในลิสต์สำหรับ interrupt/join (ถ้าใช้ poison pill ต้องส่งให้ครบจำนวน Worker ปัจจุบัน)

### 7.5 ถ้า Worker ถูก interrupt หลัง acquire resource จะป้องกัน permit หายอย่างไร

- หลัง `acquire` สำเร็จ เข้า `try { sleep(resourceMs) } finally { release }` -> interrupt ระหว่างถือ sleep จะโยน `InterruptedException` แต่ `finally` ยัง `release` ก่อนออกเสมอ
- กรณี interrupt **ระหว่างรอ** `acquire` ยังไม่มี permit จึง **ไม่ release** (เพราะ `acquire` อยู่นอก try) ไม่งั้น permit จะเกินจริง
- ผลทดสอบ: `WorkerInterruptTest` จบที่ `printer=1/1` ไม่ค้าง

### 7.6 เปรียบเทียบ Thread.State ตอนรอ Semaphore กับตอน `Thread.sleep(resourceMs)`

- รอ `Semaphore.acquire()` -> **WAITING** (ไม่มีกำหนดเวลา รอให้ Thread อื่น release มาปลุก)
- `Thread.sleep(resourceMs)` -> **TIMED_WAITING** (รอตามเวลาที่กำหนด แล้วตื่นเอง)
- ทั้งคู่ไม่กิน CPU ต่างกันที่ "ใครปลุก": sleep ถูกปลุกด้วยเวลา, acquire ถูกปลุกด้วย release ของ Thread อื่น
- (`RUNNABLE` คือกำลังทำงาน/พร้อมทำงาน, `BLOCKED` คือรอ monitor lock ของ `synchronized`)

### 7.7 ใช้สมการ TAT = WT + workMs + Resource Wait + resourceMs ชี้ว่าถ้าไม่ลง timestamp ใดน่าจะผิด

| อาการ | น่าจะผิดที่ |
|---|---|
| TAT ใหญ่กว่าผลรวมมาก | วัด `resourceWaitTotalMs` ไม่ครบ (ลืมบันทึกเวลารอ) หรือมีช่วงเวลาที่ไม่ได้นับ เช่น ระหว่าง jobStarted กับ startedAtMs |
| TAT เล็กกว่าผลรวม | `startedAtMs` ถูกบันทึกช้า/ผิดตำแหน่ง, หรือ `arrivedAtMs` ถูกตั้งช้ากว่าจริง |
| WT ติดลบ | `arrivedAtMs` ไม่ได้ตั้ง (เป็น 0) หรือใช้ `arrivalMs` แทน |
| งานไม่ใช้ resource ไม่ลงสมการ | เฉลี่ย RW รวมงาน NONE หรือ `resourceWaitTotalMs` ค่าค้างจากงานอื่น |
| ต่างกันไม่กี่ ms | ปกติ จาก `Thread.sleep` (ของเราสูงสุด 13 ms) |

### 7.8 อธิบายว่า AI ช่วยส่วนไหน และกลุ่มตรวจสอบอย่างไร (ตอบตามจริงของกลุ่ม)

แนวตอบ: ใช้ AI ช่วยอธิบายโจทย์/วางโครงเรียงตามขั้นที่อาจารย์แนะนำ/ช่วยเขียนใน TODO/ช่วยดีบัก error และกลุ่ม **ตรวจแล้วพบและแก้ข้อผิดพลาดของ AI** เช่น

1. AI เรียก `logger.log(String,String)` ซึ่งไม่มีอยู่จริง -> เปิด `ProjectLogger.java` ดูแล้วเปลี่ยนไปใช้ method จริง
2. `Statistics` ขาดตัวนับ `running` และ `snapshot()` ทำให้ Monitor ใช้ไม่ได้ -> แก้
3. `JOB_COMPLETED` ถูกพิมพ์ซ้ำสองครั้งใน `processJob` -> ตรวจจาก log (จำนวน COMPLETED เกินจำนวนงาน) แล้วลบ
4. **จุดสำคัญ:** AI ตอนแรกมองข้ามว่า Worker ที่ถูก interrupt กลางงานจะทำให้ Main ค้างที่ `awaitAllDone()` เรารันทดสอบ `WorkerInterruptTest` เห็นปัญหาแล้วเพิ่ม `jobAborted()` + `finally`
5. AI เขียนคอมเมนต์ในโค้ดทดสอบผิดว่า worker-1 ถือ PRINTER จริง ๆ คือ worker-3 -> ตรวจจาก log แล้วแก้ความเข้าใจ

---

## 8. คำถามเพิ่มเติมที่น่าจะโดน (ซ้อมไว้)

**Q: Thread ทั้งหมดมีกี่ตัว และแต่ละตัวทำอะไร?**
A: generator 1, scheduler 1, monitor 1, worker N ตามอาร์กิวเมนต์ + main (ดูหัวข้อ 1)

**Q: ทำไมต้องมี Scheduler แยกจาก Generator ทั้งที่แค่ส่งต่อ?**
A: โจทย์กำหนดให้ Generator ห้ามใส่ ReadyQueue ตรง เป็นการแยกหน้าที่ "งานเข้า" กับ "จัดงานเข้าคิวพร้อมทำ" และเป็นจุดขยายในอนาคต เช่น aging

**Q: Worker รู้ได้อย่างไรว่าไม่มีงานเหลือแล้ว?**
A: Worker ไม่รู้เอง มันหลับที่ `take()` ตลอด Main นับงานที่เสร็จด้วย latch เมื่อครบแล้ว Main `interrupt()` Worker ให้ออกจาก `take()`

**Q: ทำไมไม่ใช้ `while(true)` เช็คว่าคิวว่างหรือยัง?**
A: เป็น busy waiting กิน CPU 100% `take()` ทำให้ Thread หลับและถูกปลุกเมื่อมีงาน

**Q: `Thread.sleep` ใน Worker แทนอะไร?**
A: จำลองเวลางานจริง (workMs) และเวลาที่ถือ resource (resourceMs)

**Q: ทำไม Semaphore ใช้ fair=true?**
A: ให้ Thread ที่รอมานานได้ก่อน (FIFO) ลด starvation และทำให้ผลสม่ำเสมอ ต้องแลกกับ throughput ที่อาจต่ำลงเล็กน้อย

**Q: มี deadlock ไหม ทำไมไม่มี?**
A: ไม่มี เพราะงานหนึ่งขอ resource ได้ชนิดเดียวและไม่ถือไว้ขณะรออย่างอื่น ไม่เกิดวงจรการรอ (circular wait)

**Q: มี race condition ตรงไหนได้บ้าง และป้องกันอย่างไร?**
A: ดูตารางหัวข้อ 5

**Q: `synchronized` กับ `volatile` ต่างกันอย่างไร?**
A: `volatile` รับประกันการมองเห็นค่า (visibility) แต่ไม่ทำให้การดำเนินการหลายขั้นเป็น atomic ส่วน `synchronized` ให้ทั้ง mutual exclusion และ visibility

**Q: ทำไม `Statistics` ใช้ lock กับ `completed` แทนสร้าง lock object แยก?**
A: ใช้ได้ทั้งสองแบบ เราเลือกให้ `running` และ `completed` อยู่ใต้ lock เดียวกันเพื่อความง่ายและทำ snapshot ที่สอดคล้องกัน ถ้าจะให้ชัดกว่านี้สร้าง `private final Object lock = new Object()` ก็ได้

**Q: Throughput คำนวณจากอะไร ทำไมใช้ makespan?**
A: จำนวนงานที่เสร็จ / เวลารวมทั้งระบบ (วินาที) ตั้งแต่เริ่มจนงานสุดท้ายจบ จับ `makespanMs` ทันทีหลัง latch ครบ ก่อนปิด Thread เพื่อไม่ให้เวลาปิดระบบปนเข้ามา

**Q: ทำไม avg Resource Wait เฉลี่ยเฉพาะงานที่ใช้ resource?**
A: งานที่ไม่ใช้ resource ไม่มีเวลารอเลย ถ้ารวมด้วยค่าเฉลี่ยจะถูกดึงต่ำลงจนบอกความหนาแน่นของการแย่ง resource ไม่ได้

**Q: ถ้า workload มีงานเข้าไม่เรียงตามเวลาในไฟล์?**
A: Generator เรียงตาม `arrivalMs` ก่อนปล่อย

**Q: ถ้า `arrivalMs` เท่ากัน?**
A: เรียงต่อด้วย `sequence` (ลำดับในไฟล์)

**Q: ถ้าใส่ argument ผิด?**
A: `Config.parse` โยน exception, Main แสดง Usage แล้ว `System.exit(1)` ก่อนเริ่ม Thread ใด ๆ ไม่ใช้ค่า default เงียบ ๆ

**Q: ทำไมต้อง `join()` หลัง `interrupt()`?**
A: เพื่อยืนยันว่า Thread หยุดจริงก่อนพิมพ์สรุป และไม่ให้เหลือ Thread ค้างที่ทำให้ JVM ไม่ปิด

**Q: Monitor อ่าน `readyQueue.size()` ปลอดภัยไหม?**
A: ปลอดภัยเพราะคิวเป็น thread-safe ค่าอาจเปลี่ยนทันทีหลังอ่าน แต่ Monitor ต้องการแค่ภาพประมาณ

**Q: Priority แบบนี้ทำให้งานบางงานอดตาย (starvation) ไหม?**
A: มีความเป็นไปได้ถ้ามีงาน priority สูงเข้ามาเรื่อย ๆ เรายังไม่ได้ทำ aging (เป็นงานเสริม)

**Q: ทำไมไม่ใช้ `ExecutorService`?**
A: โจทย์ต้องการให้เห็นการจัดการ Thread, queue และ synchronization เอง

---

## 9. โจทย์แก้สดที่อาจโดน และวิธีทำ

| โจทย์ | แก้ตรงไหน |
|---|---|
| สลับ tie-break ของ Priority ให้ใช้ `arrivedAtMs` แทน `sequence` | `ReadyQueue` comparator: `.thenComparingLong(e -> e.job.arrivedAtMs)` |
| ให้ priority มาก = สำคัญกว่า | `ReadyQueue`: `comparingInt(...).reversed()` ระวังเฉพาะ priority ไม่ใช่ sequence |
| ทำให้ FCFS ใช้ `arrivalMs` แทน ticket | เปลี่ยน comparator FCFS เป็น `e.job.arrivalMs` |
| เปลี่ยน Semaphore เป็น non-fair | `new Semaphore(n, false)` ใน `ResourceManager` |
| เพิ่ม resource ชนิดใหม่ | เพิ่มใน `ResourceType`, เพิ่ม Semaphore และ case ใน `semaphoreOf` |
| ให้ Monitor รายงานทุก 500 ms | แก้ `Thread.sleep(500)` |
| ไม่ให้ Worker เกิน N งาน/ครั้ง ฯลฯ | ปรับใน `Worker.run()` |
| ใช้ `tryAcquire(timeout)` แทน `acquire` | `ResourceManager.acquire` ใช้ `tryAcquire(ms, MILLISECONDS)` และ Worker จัดการกรณีไม่ได้ (ต้องไม่ release ถ้าไม่ได้) |
| ปิดระบบด้วย poison pill แทน latch | ส่งงานพิเศษเข้า ReadyQueue เท่าจำนวน Worker และให้ Worker ออกลูปเมื่อเจอ |
| ถอด `synchronized` ออกให้ดูปัญหา | รัน workload ใหญ่หลายรอบ ดู `running` ไม่เป็น 0 หรือ `completed` ไม่ครบ (อาจเกิดไม่ทุกรอบ ให้อธิบายเหตุผลได้) |

---

## 10. ศัพท์และ API ที่ต้องอธิบายได้

| คำ | ความหมายสั้น ๆ |
|---|---|
| `Thread` / `start()` / `run()` | สร้างเธรดใหม่; `start()` สร้าง Thread จริงแล้วเรียก `run()` (เรียก `run()` ตรง ๆ จะไม่เกิดเธรดใหม่) |
| `interrupt()` | ขอให้ Thread หยุด ถ้ากำลังหลับ/รอจะโยน `InterruptedException` ถ้าไม่ได้รอจะตั้ง flag |
| `join()` | รอให้อีก Thread จบ |
| `BlockingQueue` | คิวที่ `take()` รอเมื่อว่าง `put()` รอเมื่อเต็ม |
| `PriorityBlockingQueue` | BlockingQueue ที่เรียงตาม Comparator (ไม่จำกัดขนาด) |
| `Semaphore(permits, fair)` | ตัวนับสิทธิ์; `acquire` ลด, `release` เพิ่ม; fair = FIFO |
| `CountDownLatch(n)` | ตัวนับถอยหลัง; `await()` รอจนเป็น 0; ใช้ครั้งเดียว |
| `AtomicLong` | ตัวเลขที่ปลอดภัยต่อหลาย Thread แบบไม่ใช้ lock |
| `synchronized` | mutual exclusion + visibility |
| `volatile` | visibility (ไม่ atomic สำหรับ `x++`) |
| race condition | ผลลัพธ์ขึ้นกับจังหวะการสลับ Thread |
| deadlock | Thread รอกันเป็นวงจรจนไม่มีใครไปต่อ |
| starvation | งานบางงานไม่ได้ทำเพราะถูกแซงตลอด |
| busy waiting | วนเช็กเงื่อนไขกิน CPU |
| non-preemptive | งานที่เริ่มแล้วไม่ถูกแย่ง |
| makespan | เวลาตั้งแต่เริ่มระบบจนงานสุดท้ายจบ |
| Waiting Time | เวลาตั้งแต่เข้าระบบจนถูกเริ่มทำ |
| Turnaround Time | เวลาตั้งแต่เข้าระบบจนงานเสร็จ |
| Resource Wait | เวลาที่รอ resource (ก่อน acquire สำเร็จ) |
| Throughput | งานที่เสร็จต่อวินาที |

---

## 11. สิ่งที่ต้องซ้อมก่อน Demo

1. วาดภาพรวมหัวข้อ 0 บนกระดานได้โดยไม่ดู
2. เปิดโค้ดจริงและชี้: comparator ใน ReadyQueue, `try/finally` ใน Worker, lock ใน Statistics, latch ใน Main
3. อธิบาย 5 ขั้นของ `processJob` และเหตุที่ `acquire` อยู่นอก try ด้านใน
4. เล่าบั๊กจริงที่เจอ (หัวข้อ 7.8) โดยเฉพาะ interrupt ทำให้ main ค้างและวิธีแก้
5. เปิด log แถว 1 และ 2 ชี้ลำดับ `JOB_STARTED` ที่ต่างกัน และเทียบแถว 2 กับ 7
6. ลองรันสดด้วยค่าที่อาจารย์เปลี่ยน (workers, permits, นโยบาย) แล้วทำนายผลก่อนรัน
7. ทบทวนว่า **ทุกคนในกลุ่มต้องอธิบายได้ทั้งระบบ** ไม่ใช่เฉพาะไฟล์ที่ตัวเองทำ