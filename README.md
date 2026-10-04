# Mini Job Scheduler & Shared Resource Manager

517-312 Operating Systems — Term Project

**กลุ่ม:** <ใส่ชื่อกลุ่ม>
**สมาชิก:** <ชื่อ – รหัสนักศึกษา>

---

## 1. วิธี compile

เปิด terminal ที่โฟลเดอร์ `src` แล้วรัน

```
javac *.java
```

ต้องใช้ JDK 17 ขึ้นไป (ใช้เฉพาะ library มาตรฐานของ Java)

## 2. วิธีรัน

```
java Main <workload.csv> <fcfs|priority> <workers> <printerPermits> <databasePermits>
```

| อาร์กิวเมนต์ | ความหมาย |
|---|---|
| `workload.csv` | ไฟล์งาน เช่น `../workloads/jobs_standard.csv` |
| `fcfs` / `priority` | นโยบายจัดลำดับ Ready Queue |
| `workers` | จำนวน Worker Thread (>= 1) |
| `printerPermits` | จำนวน permit ของ PRINTER (>= 1) |
| `databasePermits` | จำนวน permit ของ DATABASE (>= 1) |

ตัวอย่าง

```
java Main ../workloads/jobs_standard.csv priority 3 1 2
```

เมื่องานครบ โปรแกรมจะพิมพ์ SUMMARY แล้วจบเอง (ไม่ใช้ `System.exit`)

## 3. โครงสร้างที่ออกแบบ

```
JobGenerator --(incoming: LinkedBlockingQueue)--> Scheduler --> ReadyQueue --> Worker x N
                                                                                  |
                                          Monitor (ทุก ~1000 ms)   ResourceManager (Semaphore)   Statistics
```

| คลาส | หน้าที่ |
|---|---|
| `JobGenerator` | เรียงงานตาม `arrivalMs` รอจนถึงเวลา บันทึก `arrivedAtMs` แล้วส่งเข้า `incoming` |
| `Scheduler` | รับงานจาก `incoming` แล้วใส่ `ReadyQueue` (JobGenerator ไม่ใส่ ReadyQueue ตรง) |
| `ReadyQueue` | `PriorityBlockingQueue` + comparator ตามนโยบาย: FCFS ใช้เลข ticket (AtomicLong), Priority ใช้ priority (1 = สูงสุด) แล้วตามด้วย `sequence` |
| `Worker` | ดึงงาน -> `sleep(workMs)` -> acquire resource -> `sleep(resourceMs)` -> release (ตามลำดับที่โจทย์กำหนด) |
| `ResourceManager` | `Semaphore(fair = true)` แยก PRINTER / DATABASE |
| `Statistics` | เก็บงานที่เสร็จ, ตัวนับ running, `CountDownLatch` ให้ Main รอจนงานครบ, พิมพ์ SUMMARY |
| `Monitor` | รายงาน ready / running / completed / สถานะ resource ทุก ~1000 ms จาก snapshot |
| `Main` | อ่านค่า, สร้างและสตาร์ท Thread, รอจบ, interrupt + join ทุก Thread, พิมพ์สรุป |

## 4. การป้องกัน race condition และ deadlock

- **ReadyQueue / incoming:** ใช้ `BlockingQueue` ที่ thread-safe ในตัว Worker รอด้วย `take()` ไม่มี busy waiting
- **ตัวนับ running และ list ของงานที่เสร็จ:** แก้ไขภายใต้ `synchronized` lock ตัวเดียวกัน และ `snapshot()` อ่านภายใต้ lock นี้ ทำให้ Monitor ได้ค่า running กับ completed ที่สอดคล้องกัน
- **ฟิลด์เวลาใน `Job`:** ประกาศ `volatile` เพราะเขียนโดย Thread หนึ่งและอ่านโดยอีก Thread
- **Resource:** `acquire` อยู่นอก `try` ด้านใน, `release` อยู่ใน `finally` ดังนั้น permit ไม่รั่วแม้ถูก interrupt ระหว่างถือ และไม่ release ถ้ายังไม่เคยได้
- **ไม่เกิด deadlock:** แต่ละ Job ขอ resource ได้ชนิดเดียวและไม่ถือ resource ไว้ระหว่างรออย่างอื่น จึงไม่มีการรอเป็นวงกลม
- **ใช้นาฬิกาเดียว:** ทุกเวลามาจาก `logger.now()`

## 5. การปิดระบบ

1. ทุก Job ที่จบ (หรือถูก abort) จะ `countDown()` ใน `Statistics`
2. `Main` รอด้วย `awaitAllDone()` (ไม่ polling)
3. จากนั้น `interrupt()` Generator, Scheduler, Monitor, Worker ทั้งหมด แล้ว `join()` ทุกตัว
4. ไม่ใช้ `Thread.stop` และไม่ใช้ `System.exit` เพื่อปิดระบบ

## 6. วิธีคำนวณ metrics

- Waiting Time = `startedAtMs - arrivedAtMs`
- Turnaround Time = `finishedAtMs - arrivedAtMs`
- Resource Wait = เวลาตั้งแต่เริ่มรอจน acquire ได้ (เฉลี่ยเฉพาะงานที่ใช้ resource)
- Throughput = jobs ที่เสร็จ / makespan (วินาที)
- ตรวจสมการ Turnaround = Waiting + workMs + ResourceWait + resourceMs ได้ต่างกันไม่เกิน 13 ms (จากความไม่แม่นยำของ `Thread.sleep`)

## 7. ผลการทดลอง

Workload: `jobs_standard.csv` (แถว 5-6 ใช้ `jobs_printer.csv`) Log อยู่ในโฟลเดอร์ `logs/`

| # | Policy | Workers | Printer | DB | Avg Waiting (ms) | Avg Turnaround (ms) | Throughput (job/s) | Avg Resource Wait (ms) |
|---|---|---|---|---|---|---|---|---|
| 1 | FCFS | 3 | 1 | 2 | 2961 | 5460 | 1.15 | 79 |
| 2 | Priority | 3 | 1 | 2 | 2324 | 4940 | 1.09 | 247 |
| 3 | Priority | 1 | 1 | 2 | 8526 | 10969 | 0.41 | 1 |
| 4 | Priority | 5 | 1 | 2 | 1131 | 4070 | 1.30 | 708 |
| 5 | Priority (printer) | 3 | 1 | 2 | 1182 | 4299 | 0.79 | 1637 |
| 6 | Priority (printer) | 3 | 2 | 2 | 810 | 2625 | 1.39 | 336 |
| 7 | Priority (ทำซ้ำแถว 2) | 3 | 1 | 2 | 2338 | 4954 | 1.08 | 248 |

คำตอบ 4 ข้อและการวิเคราะห์อยู่ในเอกสารสรุป 1 หน้าแยกต่างหาก

## 8. ข้อจำกัดที่ทราบ

- งานที่ถูก interrupt กลางคันจะถูกนับเป็น aborted และไม่ถูกทำซ้ำ (ทดสอบด้วย `WorkerInterruptTest.java`: ระบบไม่ค้าง และ permit คืนครบ)
- Priority และ FCFS เป็นแบบ non-preemptive งานที่เริ่มแล้วไม่ถูกแย่ง
- Priority scheduling อาจทำให้งานลำดับต่ำรอนาน (starvation) เพราะไม่มี aging
- ค่าเวลามีความคลาดเคลื่อนระดับ ms จาก `Thread.sleep` และการ schedule ของ OS

## 9. ไฟล์ทดสอบเพิ่มเติม

- `InterruptTest.java` ทดสอบ `ResourceManager` เมื่อ Thread ถูก interrupt
- `WorkerInterruptTest.java` ทดสอบ `Worker` จริงว่าไม่ค้างและ permit ไม่หายเมื่อถูก interrupt

## 10. การใช้ AI

ดูไฟล์บันทึกการใช้ AI ของกลุ่ม (`AI_LOG.md`)