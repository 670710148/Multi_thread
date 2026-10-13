## วิธี Compile และ Run ผ่าน CMD
chcp 65001

cd src

if not exist ..\out mkdir ..\out


### 4. Compile ไฟล์ Java ทั้งหมดใน `src`


javac -d ..\out *.java


### 5. Run โปรแกรมด้วย Priority Scheduling

java -cp ..\out Main ..\workloads\jobs_standard.csv priority 3 1 2

java -cp ..\out Main ..\workloads\jobs_standard.csv fcfs 3 1 2

### เก็บlog
java -cp ..\out Main ..\workloads\jobs_standard.csv priority 3 1 2 >../logs/priority.log 2>&1

java -cp ..\out Main ..\workloads\jobs_standard.csv fcfs 3 1 2 >../logs/fcfs.log 2>&1


### ตาราง7แถว
java Main ../workloads/jobs_standard.csv fcfs 3 1 2 >../logs/r1.log 2>&1
java Main ../workloads/jobs_standard.csv priority 3 1 2 >../logs/r2.log 2>&1
java Main ../workloads/jobs_standard.csv priority 1 1 2 >../logs/r3.log 2>&1
java Main ../workloads/jobs_standard.csv priority 5 1 2 >../logs/r4.log 2>&1
java Main ../workloads/jobs_printer.csv priority 3 1 2 >../logs/r5.log 2>&1
java Main ../workloads/jobs_printer.csv priority 3 2 2 >../logs/r6.log 2>&1
java Main ../workloads/jobs_standard.csv priority 3 1 2  >../logs/r7.log 2>&1 (รันซ้ำแถว 2 โดยไม่เปลี่ยนค่าใด)

### 6. ตรวจสอบและลบไฟล์ `.class` เก่าใน `src` (กันรก!!!!!!1)

ตรวจสอบไฟล์ `.class` ก่อน:

dir ..\out\*.class

ลบไฟล์ `.class` ในโฟลเดอร์ปัจจุบัน:

del ..\out\*.class


### 7. คำสั่งย่อสำหรับรันครั้งถัดไป

เมื่ออยู่ในโฟลเดอร์ `src` แล้ว ให้ใช้สองคำสั่งนี้:

javac -d ..\out *.java
java -cp ..\out Main ..\workloads\jobs_standard.csv priority 3 1 2

**ข้อควรระวัง:** คำสั่ง `*.java` ใช้กับไฟล์ Java ที่อยู่ใน `src` โดยตรง หากมีไฟล์ในโฟลเดอร์ย่อยหรือใช้ `package` อาจต้องปรับคำสั่ง Compile ให้ตรงกับโครงสร้างโปรเจกต์