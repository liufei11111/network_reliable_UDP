Programming Assignment 2
Fei Liu
fl2312@columbia.edu
—————————————
My goal is to recover as many point from midterm as possible. So, I did what I think deserve all extra-credit for this assignment. The below file is long but it worth reading.
Thank you.

All required functions are realized. I also implemented multiple window size that you can test in this folder and other features you can see in the third test case.

NOTE THAT: EXTRA_CREDIT folder is for added function part with HW1. Please finish testing extra credit features in this part before you go to that one. Because some features I introduce(like TIMEOUT and debug mode) may be interpreted as bugs if you didn’t go through all TEST cases in this one. Thank you.

A quick cap of what extra credit things I did (TEST CASE 3 has details):
By setting a limit a packet could be retransmitted and how long a server is going to wait after receiving previous ACK, the server can detect that a receiver is dropped off or not. Similarly, if the receiver wait for 50 seconds and nothing is received. It quits as well with a message saying interrupted. So, this app is interruption safe. I rearrange the packets in an buffered ArrayList and can be retransmitted very fast. The time to send another packet is automatically updated so that the maximum speed can be reached after first a few packets which assume a slower sending rate in the beginning. I implemented a DEBUG which will be used in below test cases to facilitate your testing.
NOTE:: If you want to test three error flags all together, better use debug mode, which will facilitate your looking into what is going on. Just add “-d” to the end of command that you normally do.

TEST CASES:

Note: Please note that I used a TCP to send ACK’s. So, if you have Proxy, Receiver, and Sender at three different machines, the Sender would need IP address for two machines. This does not conform to the format specified. So, I set it to be tested on two machines. Client and Proxy is on the same machine while the Server is on another.
If you want to see the log files, please use “cat <log file name>”. Just use vim or emac may not show it properly because I used the toString() method of the Timestamp. So, it is a little under formatted under VIM mode. But, it is perfectly fine with “cat”
—————————————————————————

(Test Case 1) General Functionality:
1.You need three terminals. log in to clic account by doing so: ssh <your UNI>@128.59.15.33 and in the other two terminals ssh <your UNI>@128.59.15.34
There two are CLIC machines I tested on. It’s easier to just follow my IP because you can just copy below command and have it run. Please use my order of commands as well because I have to make sure TCP connection is built first before UDP starts. 
Optionally, “-d” option at the end of the command is the option to open the debug mode. In this mode you can see all the logs of UDP pkt’s and ACK’s pop on when it is processed. They will be convenient for latter test cases. 
Purpose:
1. This is the baseline test of functions. Notice that we used the Makefile as the file to send. It is small but it shows the necessary things. 
(There is a 4 second halt after the TCP is built and before the Sender starts sending because there is a Client side overhead to start listening and if you send packages too early they will be missed and you have to retransmit it again. This will mess up the statistics.Please open up the)
—-

proxy

IP of this machine: 128.59.15.34

./newudpl -o 128.59.15.34/2000 -i 128.59.15.33/9000 -p5000:6000 (-L 09 -O 09 -B 08) <- this is optional fields. 

Since this file is small, it is convenient to use (-L 09 -O 09 -B 08) flags to see the logs. I will have a bigger file test latter.

—-

Sender

IP of this machine: 128.59.15.33

java Sender Makefile 128.59.15.34 5000 9999 1 logfile_Server.txt  (-d) <- this is an optional debugging mode
—-

Receiver

IP of this machine: 128.59.15.34

java Receiver file_Client.txt 2000 128.59.15.33 9999 logfile_Client.txt (-d)  <- this is an optional debugging mode

—-
Please use “cat logfile_Server.txt” and “logfile_Client.txt” to see the logs

The desired result should be something like below:
—-
TCP Connection for ACK is Built!
Num of bytes loaded: 2670
------------------
Delivery completed successfully
Total bytes sent = 3230
Total segments sent = 6
Segments sent retransmitted = 1

—————————————————————————

(Test Case 2) Big file test:
Note: I prepare you a mp3 file(Columbia Fight song: Roar, Lion, Roar) to test in this test case. Below features can be tested in one command.
Purpose:
1. The debug mode is helpful here since it will show that the speed of sending file after receiving several packets goes up dramatically. You can see the frequency of log’s popping up is much faster latter. This is because I measure the RTT and adjusted the speed. 
2. In a second run of this part, please also have the debug mode on so that you can see what’s going on. You can ctrl-c on the proxy machine to simulate a delay of package transmission and reenter the proxy command to see if it restores. 
(But, please be noted that the Sender will exit if it has retransmitted a packet for 100000 times, or it has waited for 20 seconds. These are set in the beginning of my code as static variables.So, please don’t halt the proxy for too long. This is a feature not a bug.)
3. Feel free to use the options as of below. Please don’t use 99 though. They is painfully slow. There are 600 or more packets. Some of those situation will occur. You only need to do 

“diff file_Client.txt a.mp3” to see it is correct.

—-

proxy

IP of this machine: 128.59.15.34

./newudpl -o 128.59.15.34/2000 -i 128.59.15.33/9000 -p5000:6000 -L 09 -O 09 -B 08

—-

Sender

IP of this machine: 128.59.15.33

java Sender a.mp3 128.59.15.34 5000 9999 1 logfile_Server.txt  (-d) <- this is an optional debugging mode
—-

Receiver

IP of this machine: 128.59.15.34

java Receiver file_Client.txt 2000 128.59.15.33 9999 logfile_Client.txt (-d)  <- this is an optional debugging mode

—-
Please use “cat logfile_Server.txt” and “logfile_Client.txt” to see the logs
—-
The desired result for windows size 1 should be something like below: 
—-
TCP Connection for ACK is Built!
Num of bytes loaded: 357500
------------------
Delivery completed successfully
Total bytes sent = 358060
Total segments sent = 640
Segments sent retransmitted = 1

—-
The desired result for windows size 3 should be something like below: 
—-
TCP Connection for ACK is Built!
Num of bytes loaded: 357500
------------------
Delivery completed successfully
Total bytes sent = 1070820
Total segments sent = 1914
Segments sent retransmitted = 1275

—————————————————————————


(Test Case 3) EXTRA-CREDIT test:
Note: I prepare you a mp3 file(Columbia Fight song: Roar, Lion, Roar) to test in this test case. Below features can be tested in one command.
Please be noted that if you interrupt, the file will be wrong. You will see this on the statistics session if the file is not successfully sent the server will indicate that the delivery is disrupted.
Purpose:
1. n window size. This guy perfectly support any window size that can be fitted into memory. Try, man!
2. I implemented an interruption mechanism such that if you ctrl-c on client side, the server will detect and exit right away. If you ctrl-c for the proxy, the sender will keep sending until the above conditions are reached. If you ctrl-c at the server side, the client will wait for 20 seconds and if still nothing, it will exit. However, to see any error message, you need to be in the debug mode.
3. You may wonder is file properly handled. Yes, any file you want to write is tested if they exist and if so, they are deleted first and start a new file to write to.
4. In debug mode, you can do whatever and see an error message for that. Sometimes, you see a error message but the program goes on in the debug mode. This is because this is not a bug and I actually take advantage of the TimeOut option of the socket to break potential deadlock when the windows size is not 1 or there is a lost packet and the Sender waits more than it should be. Again, this is a feature. I used SocketException to handle the deadlock.
5. Fast retransmission! I used ArrayList to preprocess any packet before sending them. So, if you missed it somewhere, no worries. They are in the memory. So, just resent it. I keep track of an index ArrayList that comes along with the packet ArrayList. This index will tell use whether the packet has been transmitted.
6. I AM GOING TO IMPLEMENT THE EXTRA CREDIT WITH HW1 IN ANOTHER FOLDER PLEASE READ THE READ ME IN THAT FOLDER FOR TESTING.

“diff file_Client.txt a.mp3” to see it is correct.
The below commands are the same as in test case 2 because what you do is chaining the windows size, use debug mode, and use ctrl-c.
—-

proxy

IP of this machine: 128.59.15.34

./newudpl -o 128.59.15.34/2000 -i 128.59.15.33/9000 -p5000:6000 -L 09 -O 09 -B 08

—-

Sender

IP of this machine: 128.59.15.33

java Sender a.mp3 128.59.15.34 5000 9999 1 logfile_Server.txt  (-d) <- this is an optional debugging mode
—-

Receiver

IP of this machine: 128.59.15.34

java Receiver file_Client.txt 2000 128.59.15.33 9999 logfile_Client.txt (-d)  <- this is an optional debugging mode

—-
Please use “cat logfile_Server.txt” and “logfile_Client.txt” to see the logs
—-
The desired result for windows size 1 should be something like below: 
—-
TCP Connection for ACK is Built!
Num of bytes loaded: 357500
------------------
Delivery completed successfully
Total bytes sent = 358060
Total segments sent = 640
Segments sent retransmitted = 1

—-
The desired result for windows size 3 should be something like below: 
—-
TCP Connection for ACK is Built!
Num of bytes loaded: 357500
------------------
Delivery completed successfully
Total bytes sent = 1070820
Total segments sent = 1914
Segments sent retransmitted = 1275

—————————————————————————


