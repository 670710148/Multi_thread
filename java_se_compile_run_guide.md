## วิธี Compile และ Run ผ่าน CMD

<!-- ### 1. เข้าโฟลเดอร์โปรเจกต์

```cmd
cd /d "C:\Users\TAMCOM\Desktop\Project\os-term-project-starter\os-term-project-starter"
``` -->

### 2. เข้าโฟลเดอร์ `src`

```cmd
cd src
```

### 3. สร้างโฟลเดอร์ `out`

```cmd
if not exist ..\out mkdir ..\out
```

### 4. Compile ไฟล์ Java ทั้งหมดใน `src`

```cmd
javac -d ..\out *.java
```

### 5. Run โปรแกรมด้วย Priority Scheduling

```cmd
java -cp ..\out Main ..\workloads\jobs_standard.csv priority 3 1 2
```

หากต้องการใช้ FCFS ให้เปลี่ยนเป็น:

```cmd
java -cp ..\out Main ..\workloads\jobs_standard.csv fcfs 3 1 2
```

### 6. ตรวจสอบและลบไฟล์ `.class` เก่าใน `src` (ถ้ามี)

ตรวจสอบไฟล์ `.class` ก่อน:

```cmd
dir ..\out\*.class
```

ลบไฟล์ `.class` ในโฟลเดอร์ปัจจุบัน:

```cmd
del ..\out\*.class
```

### 7. คำสั่งย่อสำหรับรันครั้งถัดไป

เมื่ออยู่ในโฟลเดอร์ `src` แล้ว ให้ใช้สองคำสั่งนี้:

```cmd
javac -d ..\out *.java
java -cp ..\out Main ..\workloads\jobs_standard.csv priority 3 1 2
```

**ข้อควรระวัง:** คำสั่ง `*.java` ใช้กับไฟล์ Java ที่อยู่ใน `src` โดยตรง หากมีไฟล์ในโฟลเดอร์ย่อยหรือใช้ `package` อาจต้องปรับคำสั่ง Compile ให้ตรงกับโครงสร้างโปรเจกต์