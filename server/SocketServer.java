import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;
import java.util.concurrent.Callable;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.GpioPinPwmOutput;
import com.pi4j.io.gpio.PinMode;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.trigger.GpioCallbackTrigger;
import com.pi4j.wiringpi.Gpio;
import com.pi4j.wiringpi.SoftPwm;

public class SocketServer {

	private ServerSocket serverSocket;
	public static final int SERVERPORT = 6000;

	// ================
	// Commands
	// ================
	private static String LIGHT_ON = "light_on";
	private static String LIGHT_OFF = "light_off";
	private static String DOOR_OPEN = "door_open";
	private static String DOOR_CLOSE = "door_close";

	// ================
	// GPIO block
	// ================
	final GpioController gpio = GpioFactory.getInstance();
	final GpioPinDigitalOutput lightPin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "MyLED", PinState.LOW);
	final GpioPinDigitalInput myButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_02, PinPullResistance.PULL_DOWN);

	Socket socket = null;
	GpioCallbackTrigger doorRingTrigger;

	public SocketServer() {
		try {
			initializeSoftPWM();
			System.out.println("Waiting for connect ...");
			createSocket();
			System.out.println("Device is connected");
			createDoorBell();
			createCommandReader();
			// Wait until user stops the application
			Scanner scanIn = new Scanner(System.in);
			String nextLine = "";
			while (true) {
				nextLine = scanIn.nextLine();
				if (nextLine.equals("!exit")) {
					System.exit(0);
				}
				;
			}
		} catch (SocketException e) {
			System.out.println(String.format("Socket connect is broken: \n %s", e.getMessage()));
			System.exit(0);
		} catch (IOException e) {
			System.out.println(String.format("Something wrong: \n %s", e.getMessage()));
			System.exit(0);
		}
	}

	private void createSocket() throws IOException, SocketException {
		serverSocket = new ServerSocket(SERVERPORT);
		socket = serverSocket.accept();
		socket.setKeepAlive(true);
	}

	private void initializeSoftPWM() {
		Gpio.wiringPiSetup();
		SoftPwm.softPwmCreate(26, 0, 100);
	}

	private void createCommandReader() {
		new Thread(new CommandReader()).start();
	}

	private class CommandReader implements Runnable {

		public void run() {
			try {
				BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				while (true) {
					String read = input.readLine();
					if (read != null && !read.isEmpty()) {
						System.out.println(String.format("Message: '%s' was received", read));
						reactToCommand(read);
					}
				}
			} catch (IOException e) {
				System.out.println(String.format("Cannot create input stream: \n %s", e.getMessage()));
			}
		}

	}

	private void createDoorBell() {
		new Thread(new DoorRingListener()).start();
	}

	private class DoorRingListener implements Runnable {

		public void run() {
			try {
				PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
				doorRingTrigger = creareDoorRingTrigger(out);
				myButton.addTrigger(doorRingTrigger);
			} catch (IOException e) {
				System.out.println(String.format("Cannot create output stream: \n %s", e.getMessage()));
			}
		}

	}

	private GpioCallbackTrigger creareDoorRingTrigger(final PrintWriter out) {
		return new GpioCallbackTrigger(new Callable<Void>() {
			public Void call() throws Exception {
				myButton.removeAllTriggers();
				System.out.println("Someone comes to home");
				sendNotificationToClient(out);
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
				}
				myButton.addTrigger(doorRingTrigger);
				return null;
			}
		});
	}

	private void sendNotificationToClient(PrintWriter out) {
		out.println("Someone comes to home");
		out.flush();
	}

	private void reactToCommand(String read) {
		if (read.equalsIgnoreCase(LIGHT_ON)) {
			turnLightOn();
		} else if (read.equalsIgnoreCase(LIGHT_OFF)) {
			turnLightOff();
		} else if (read.equalsIgnoreCase(DOOR_OPEN)) {
			openDoor();
		} else if (read.equalsIgnoreCase(DOOR_CLOSE)) {
			closeDoor();
		}
	}

	private void closeDoor() {
		SoftPwm.softPwmWrite(26, 5);
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		SoftPwm.softPwmWrite(26, 00);
	}

	private void openDoor() {
		SoftPwm.softPwmWrite(26, 22);
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		SoftPwm.softPwmWrite(26, 00);
	}

	private void turnLightOff() {
		lightPin.low();
		System.out.println(String.format("Light was turned off"));
	}

	private void turnLightOn() {
		lightPin.high();
		System.out.println(String.format("Light was turned on"));
	}

	public static void main(String[] args) {
		new SocketServer();
	}
}
