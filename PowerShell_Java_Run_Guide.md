## วิธี Compile และ Run ผ่าน PowerShell

### 1. เข้าโฟลเดอร์โปรเจกต์

```powershell
cd "C:\Users\TAMCOM\Desktop\Project\os-term-project-starter\os-term-project-starter"
```

### 2. ตรวจสอบ Java JDK

```powershell
java -version
javac -version
```

ต้องใช้ JDK 17 ขึ้นไป

### 3. เข้าโฟลเดอร์ `src`

```powershell
cd src
```

### 4. สร้างโฟลเดอร์ `out`

```powershell
New-Item -ItemType Directory -Force -Path ..\out
```

### 5. Compile ไฟล์ Java ทั้งหมด

```powershell
javac -d ..\out (Get-ChildItem -Filter *.java).Name
```

ไฟล์ `.class` จะถูกเก็บไว้ในโฟลเดอร์ `out`

### 6. Run โปรแกรมด้วย Priority Scheduling

```powershell
java -cp ..\out Main ..\workloads\jobs_standard.csv priority 3 1 2
```

หากต้องการใช้ FCFS ให้เปลี่ยนเป็น:

```powershell
java -cp ..\out Main ..\workloads\jobs_standard.csv fcfs 3 1 2
```

**ความหมายของ Arguments**

| Argument | ความหมาย |
|---|---|
| `..\out` | โฟลเดอร์เก็บไฟล์ `.class` |
| `Main` | คลาสหลักของโปรแกรม |
| `..\workloads\jobs_standard.csv` | ไฟล์ workload |
| `priority` หรือ `fcfs` | นโยบายจัดลำดับงาน |
| `3` | จำนวน Worker Threads |
| `1` | จำนวน Printer Permits |
| `2` | จำนวน Database Permits |

### 7. ตรวจสอบและลบไฟล์ `.class` เก่าใน `src` (ถ้ามี)

ตรวจสอบก่อน:

```powershell
Get-ChildItem -Filter *.class
```

ลบไฟล์ `.class` ในโฟลเดอร์ปัจจุบัน:

```powershell
Get-ChildItem -Filter *.class | Remove-Item
```

### 8. คำสั่งย่อสำหรับรันครั้งถัดไป

เมื่อสร้างโฟลเดอร์ `out` แล้ว และอยู่ในโฟลเดอร์ `src` ให้ใช้สองคำสั่งนี้:

```powershell
javac -d ..\out (Get-ChildItem -Filter *.java).Name
java -cp ..\out Main ..\workloads\jobs_standard.csv priority 3 1 2
```

เปลี่ยน `priority` เป็น `fcfs` ได้ตามต้องการ

**ข้อควรระวัง:** คำสั่ง Compile นี้ครอบคลุมไฟล์ `.java` ใน `src` โดยตรง หากมีไฟล์ในโฟลเดอร์ย่อยหรือใช้ `package` อาจต้องปรับคำสั่งให้เหมาะกับโครงสร้างโปรเจกต์