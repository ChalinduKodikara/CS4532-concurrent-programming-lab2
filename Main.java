import java.util.Random;
import java.util.concurrent.Semaphore;

public class Main {

    // Set rider arrival mean time to 30 seconds
    static float passengerArrivalMeanTime = 30f * 1000;

    // Set bus arrival mean time to 20 minutes
    static float busArrivalMeanTime = 20 * 60f * 1000;

    public static void main(String[] args) {
        // Create bus and rider generators
        BusGenerator busGenerator = new BusGenerator(busArrivalMeanTime);
        PassengerGenerator passengerGenerator = new PassengerGenerator(passengerArrivalMeanTime);

        // Create two threads for generators
        Thread busGeneratorThread = new Thread(busGenerator);
        Thread riderGeneratorThread = new Thread(passengerGenerator);

        // Run threads
        busGeneratorThread.start();
        riderGeneratorThread.start();

    }
}

class BusStop {
    /*
    This class will hold shared variables
     */

    // Indexes of the current riders and buses
    public static int passengerIndex = 1;
    public static int busIndex = 1;

    // Shared variable to keep track of how many riders/passengers are waiting
    public static int passengers = 0;

    // mutex is used to protect the riders variable
    public static Semaphore mutex = new Semaphore(1);

    // This semaphore makes sure there are no more than 50 riders in the bus stop. If there are 50 riders, next threads cannot enter
    public static Semaphore busStopCapacity = new Semaphore(50);

    // Semaphore to signal when the bus is arrived, then riders can get in
    public static Semaphore bus = new Semaphore(0);

    // Semaphore to signal when all the waiting riders have got into the bus
    public static Semaphore allBoarded = new Semaphore(0);

    // Method to return rider index
    public static int getPassengerIndex() {
        return BusStop.passengerIndex;
    }

    // Method to return bus index
    public static int getBusIndex() {
        return BusStop.busIndex;
    }

    // Method to increment rider index
    public static void incrementPassengerIndex() {
        BusStop.passengerIndex++;
    }

    // Method to increment bus index
    public static void incrementBusIndex() {
        BusStop.busIndex++;
    }
}

class PassengerGenerator implements Runnable{

    // This is used to generate random arrival times
    static Random random;

    float passengerArrivalMeanTime;

    public PassengerGenerator(float passengerArrivalMeanTime) {
        this.passengerArrivalMeanTime = passengerArrivalMeanTime;
        random = new Random();
    }

    @Override
    public void run() {
        while (true) {


            try {
                Passenger passenger = new Passenger(BusStop.getPassengerIndex());
                Thread passengerThread = new Thread(passenger);

                passengerThread.start();

                // Sleep the thread around arrival time of passengers. So after that another passenger will arrive
                Thread.sleep(this.calculatePassengerSleepTime(passengerArrivalMeanTime, random));
                // Since one passenger is arrived, the passenger count is increased by 1
                BusStop.incrementPassengerIndex();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // To calculate the arrival time between riders, we can use the mean arrival time provided (30 seconds)
    // and the exponential distribution to generate random arrival times. We can use a random number
    // generator to generate a random number between 0 and 1, and then use the inverse cumulative distribution
    // function (ICDF) of the exponential distribution to convert this number to a time value.
    private long calculatePassengerSleepTime(float passengerArrivalMeanTime, Random random) {
        // Equation is time = -mean * ln(1-random_number), mean = 1/lambda
        float lambda = 1 / passengerArrivalMeanTime;
        return Math.round(-Math.log(1-random.nextFloat()) / lambda);
    }
}

class Passenger implements Runnable{

    // Index of the current passenger
    int passengerIndex;

    public Passenger(int passengerIndex) {
        this.passengerIndex = passengerIndex;
    }

    // Passenger is boarded into the bus
    private void boardBus() {
        System.out.println("Rider Number: " + this.passengerIndex + " boarded");
    }

    @Override
    public void run() {
        try {
            // When a passenger is arrived, bus stop capacity is reduced by 1. Only 50
            // passengers can be in the bus stop. Others has to wait
            BusStop.busStopCapacity.acquire();
            System.out.println("Rider Number: " + this.passengerIndex + " arrived at the bus stop");

            // Increment the num of passengers. We lock it to avoid race conditions
            // We use semaphore to lock it

            // The passengers that comes after bus arrived will be waited by this mutex lock
            // It will not allow new passengers to get into the bus
            BusStop.mutex.acquire();
            BusStop.passengers++;
            BusStop.mutex.release();

            // Passenger wait until bus is arrived, this will sleep the passenger thread until bus arrives
            BusStop.bus.acquire();

            // Get into the bus
            BusStop.busStopCapacity.release();
            boardBus();

            // No need to lock this section as only one thread can go to this area at a time
            BusStop.passengers--;

            if (BusStop.passengers == 0) {
                System.out.println("All riders were got in, Bus is departing...");

                // If all riders/passengers have boarded, wake the bus thread to depart
                BusStop.allBoarded.release();
            }
            else {
                // If the number of passengers/riders waiting is not 0 yet, that means there are more
                // riders waiting to get into. So the bus semaphore must be relased so that other threads can get in
                BusStop.bus.release();
            }


        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

class BusGenerator implements Runnable{
    static Random random;

    // Variable to hold bus arrival mean time
    float busArrivalMeanTime;
    BusGenerator (float busArrivalMeanTime) {
        this.busArrivalMeanTime = busArrivalMeanTime;
        random = new Random();
    }

    @Override
    public void run() {
        while (true) {

            try {
                // Sleep the thread around arrival time of buses. So after that another bus will arrive
                Thread.sleep(this.calculateBusSleepTime(busArrivalMeanTime, random));
                Bus bus = new Bus(BusStop.getBusIndex());
                Thread busThread = new Thread(bus);
                busThread.start();
                // Since one passenger is arrived, the passenger count is increased by 1
                BusStop.incrementBusIndex();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // To calculate the arrival time between buses, we can use the mean arrival time provided
    // and the exponential distribution to generate random arrival times. We can use a random number
    // generator to generate a random number between 0 and 1, and then use the inverse cumulative distribution
    // function (ICDF) of the exponential distribution to convert this number to a time value.
    private long calculateBusSleepTime(float busArrivalMeanTime, Random random) {
        // Equation is time = -mean * ln(1-random_number)
        float lambda = 1 / busArrivalMeanTime;
        return Math.round(-Math.log(1-random.nextFloat()) / lambda);
    }
}

class Bus implements Runnable{

    int busIndex;
    int numOfPassengers;
    public Bus(int busIndex) {
        this.busIndex = busIndex;
    }

    private void departBus() {
        System.out.println("Bus number " + this.busIndex + " is departed with " + this.numOfPassengers + " riders");
    }

    @Override
    public void run() {
        try {
            // Once bus is arrived, acquire the mutex lock. So passengers who arrive after bus is arrived cannot get into the bus
            BusStop.mutex.acquire();
            System.out.println("Bus number " + this.busIndex + " arrived at the bus stop. There are " + BusStop.passengers + " riders waiting for the bus");


            if (BusStop.passengers == 0) {
                System.out.println("Bus number "+ this.busIndex + " is departing since there are 0 riders");
            } else {
                numOfPassengers = BusStop.passengers;
                // Awake riders/passenger who are waiting at the bus stop
                BusStop.bus.release();

                // Wait (Sleep) until everyone is boarded
                BusStop.allBoarded.acquire();
            }

            // Allow other riders/passengers to wait for the next bus
            BusStop.mutex.release();

            // Depart the current bus
            departBus();



        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}