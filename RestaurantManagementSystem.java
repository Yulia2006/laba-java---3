import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class Order {
    private static final AtomicInteger idCounter = new AtomicInteger(1);  // AtomicInteger для потокобезопасной генерации ID
    private final int id;
    private final String customerName;
    private final String dish;
    private final long orderTime;
    private long cookingStartTime;
    private long cookingEndTime;
    private String status;
    private final String waiterName;

    public Order(String customerName, String dish, String waiterName) {
        this.id = idCounter.getAndIncrement();  // Атомарное инкрементирование без блокировок
        this.customerName = customerName;
        this.dish = dish;
        this.waiterName = waiterName;
        this.orderTime = System.currentTimeMillis();
        this.status = "Создан";
    }

    public int getId() { return id; }
    public String getCustomerName() { return customerName; }
    public String getDish() { return dish; }
    public String getWaiterName() { return waiterName; }
    public String getStatus() { return status; }

    public void setCookingStartTime(long time) {
        this.cookingStartTime = time;
        this.status = "Готовится";
    }

    public void setCookingEndTime(long time) {
        this.cookingEndTime = time;
        this.status = "Готово";
    }

    public long getCookingTime() {
        return cookingEndTime - cookingStartTime;
    }

    public long getTotalTime() {
        return cookingEndTime - orderTime;
    }

    public String toString() {
        return String.format("Заказ #%d: %s для %s (официант: %s, статус: %s)",
                id, dish, customerName, waiterName, status);
    }
}

class Waiter implements Runnable {
    private final String name;
    // BlockingQueue - потокобезопасная блокирующая очередь для заказов на кухню
    private final BlockingQueue<Order> kitchenQueue;
    // ConcurrentLinkedQueue - неблокирующая очередь для готовых заказов
    private final ConcurrentLinkedQueue<Order> completedOrders;
    private volatile boolean isWorking = true;  // volatile для видимости изменений между потоками
    private final List<String> menu;
    private final AtomicInteger servedCustomers = new AtomicInteger(0);  // Атомарный счетчик
    private static final String[] CUSTOMER_NAMES = {
            "Алексей", "Мария", "Иван", "Ольга", "Дмитрий",
            "Екатерина", "Сергей", "Анна", "Павел", "Наталья"
    };

    public Waiter(String name, BlockingQueue<Order> kitchenQueue,
                  ConcurrentLinkedQueue<Order> completedOrders, List<String> menu) {
        this.name = name;
        this.kitchenQueue = kitchenQueue;
        this.completedOrders = completedOrders;
        this.menu = menu;
    }

    public void stop() {
        isWorking = false;  // Установка флага остановки
    }

    public int getServedCount() {
        return servedCustomers.get();
    }

    public String getWaiterName() {
        return name;
    }

    private void takeOrder() throws InterruptedException {
        Thread.sleep(new Random().nextInt(500) + 300);  // Время принятия заказов

        String customer = CUSTOMER_NAMES[new Random().nextInt(CUSTOMER_NAMES.length)];
        String dish = menu.get(new Random().nextInt(menu.size()));

        Order order = new Order(customer, dish, name);
        System.out.printf("%s принял заказ: %s%n", name, order);

        // put() блокирует поток если очередь полна (максимум 50 заказов)
        kitchenQueue.put(order);
        System.out.printf("%s передал заказ #%d на кухню (в очереди: %d)%n",
                name, order.getId(), kitchenQueue.size());

        waitForOrder(order);
    }

    private void waitForOrder(Order order) throws InterruptedException {
        // официант периодически проверяет готовность
        while (isWorking) {
            Thread.sleep(200);  // Пауза для снижения нагрузки на CPU

            // удаление заказа из очереди готовых
            if (completedOrders.remove(order)) {
                servedCustomers.incrementAndGet();  // Атомарное инкрементирование
                System.out.printf("%s отнёс заказ #%d клиенту %s (время приготовления: %dмс)%n",
                        name, order.getId(), order.getCustomerName(), order.getCookingTime());
                return;
            }
        }
    }

    public void run() {
        System.out.printf("%s начал работу%n", name);
        try {
            // Основной цикл работы официанта с проверкой флага остановки
            while (isWorking && !Thread.currentThread().isInterrupted()) {
                takeOrder();
                Thread.sleep(new Random().nextInt(1000) + 500);  // Пауза между заказами
            }
        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
        }
        System.out.printf("%s завершил работу. Обслужено клиентов: %d%n",
                name, servedCustomers.get());
    }
}

class Chef implements Runnable {
    private final String name;
    private final Order order;
    private final ConcurrentLinkedQueue<Order> completedOrders;
    // Статическая map для времени приготовления - общая для всех поваров
    public static final Map<String, Integer> COOKING_TIMES = new HashMap<>();

    static {
        COOKING_TIMES.put("Пицца Маргарита", 3000);
        COOKING_TIMES.put("Паста Карбонара", 2500);
        COOKING_TIMES.put("Стейк Рибай", 4000);
        COOKING_TIMES.put("Салат Цезарь", 1500);
        COOKING_TIMES.put("Суп Том Ям", 2000);
        COOKING_TIMES.put("Бургер", 1800);
        COOKING_TIMES.put("Суши сет", 3200);
        COOKING_TIMES.put("Десерт Тирамису", 1200);
        COOKING_TIMES.put("Греческий салат", 1000);
        COOKING_TIMES.put("Лазанья", 2800);
        COOKING_TIMES.put("Рыба на гриле", 2200);
        COOKING_TIMES.put("Крем-брюле", 1300);
    }

    public Chef(String name, Order order, ConcurrentLinkedQueue<Order> completedOrders) {
        this.name = name;
        this.order = order;
        this.completedOrders = completedOrders;
    }

    public void run() {
        order.setCookingStartTime(System.currentTimeMillis());
        System.out.printf("%s начал готовить %s (заказ #%d)%n",
                name, order.getDish(), order.getId());

        try {
            int cookingTime = COOKING_TIMES.getOrDefault(order.getDish(), 2000);
            Thread.sleep(cookingTime + new Random().nextInt(1000));  // Имитация времени приготовления

            order.setCookingEndTime(System.currentTimeMillis());
            completedOrders.add(order);  // Добавление готового заказа в очередь

            System.out.printf("%s приготовил %s (заказ #%d, время: %dмс)%n",
                    name, order.getDish(), order.getId(), order.getCookingTime());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

public class RestaurantManagementSystem {
    // BlockingQueue с ограничением размера - предотвращает неограниченный рост памяти
    private final BlockingQueue<Order> kitchenQueue = new LinkedBlockingQueue<>(50);

    // ConcurrentLinkedQueue - позволяет множественным потребителям без блокировок
    private final ConcurrentLinkedQueue<Order> completedOrders = new ConcurrentLinkedQueue<>();

    //  управление потоками поваров
    private final ExecutorService kitchen = Executors.newFixedThreadPool(4);

    private final List<Waiter> waiters;
    private final List<String> menu;
    private volatile boolean isRunning = false;  // volatile для межпоточной видимости
    private Thread restaurantThread;
    private final List<String> chefNames;
    private int totalOrdersServed = 0;
    private final Scanner scanner;

    public RestaurantManagementSystem() {
        this.waiters = new ArrayList<>();
        this.chefNames = Arrays.asList("Шеф Андрей", "Повар Михаил", "Повар Елена", "Повар Алексей");
        this.scanner = new Scanner(System.in);


        this.menu = new ArrayList<>(Arrays.asList(
                "Пицца Маргарита",
                "Паста Карбонара",
                "Стейк Рибай",
                "Салат Цезарь",
                "Суп Том Ям",
                "Бургер",
                "Суши сет",
                "Десерт Тирамису",
                "Греческий салат",
                "Лазанья",
                "Рыба на гриле",
                "Крем-брюле"
        ));
    }

    public void start() {
        if (isRunning) {
            System.out.println("Ресторан уже работает!");
            return;
        }

        isRunning = true;

        // Создание и запуск потоков-официантов
        String[] waiterNames = {"Официант Игорь", "Официант Светлана", "Официант Виктор", "Официант Анна"};
        for (String name : waiterNames) {
            Waiter waiter = new Waiter(name, kitchenQueue, completedOrders, menu);
            waiters.add(waiter);
            new Thread(waiter, name + "_Thread").start();
        }

        // Главный диспетчерский поток для кухни
        restaurantThread = new Thread(() -> {
            System.out.println("Ресторан открыт! Кухня начала работу.");

            // Основной цикл диспетчера с двойной проверкой условий выхода
            while (isRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    // poll() с таймаутом вместо take() для корректного завершения
                    Order order = kitchenQueue.poll(100, TimeUnit.MILLISECONDS);

                    if (order != null) {
                        String chefName = chefNames.get(new Random().nextInt(chefNames.size()));
                        Chef chef = new Chef(chefName, order, completedOrders);
                        kitchen.submit(chef);
                        totalOrdersServed++;
                    }

                    Thread.sleep(100);  // Пауза для снижения нагрузки на CPU
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();  // Восстановление флага прерывания
                    break;
                }
            }
        }, "Kitchen_Dispatcher_Thread");

        restaurantThread.start();
        System.out.println("Ресторан начал работу с " + waiters.size() + " официантами и " +
                chefNames.size() + " поварами");
    }

    public void stop() {
        isRunning = false;  // Сигнал остановки для всех потоков

        System.out.println("\nЗавершение работы официантов...");
        for (Waiter waiter : waiters) {
            waiter.stop();  // Установка флага остановки для официантов
        }

        System.out.println("Завершение работы кухни...");
        // Graceful shutdown пула потоков
        kitchen.shutdown();
        try {
            // Ожидание завершения текущих задач
            if (!kitchen.awaitTermination(5, TimeUnit.SECONDS)) {
                kitchen.shutdownNow();  // Принудительное завершение
            }
        } catch (InterruptedException e) {
            kitchen.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Прерывание диспетчерского потока
        if (restaurantThread != null && restaurantThread.isAlive()) {
            restaurantThread.interrupt();
        }

        System.out.println("Ресторан закрыт!");
        printStatistics();
    }

    public void printStatistics() {
        System.out.println("\n СТАТИСТИКА РАБОТЫ РЕСТОРАНА");
        System.out.println("Всего обслужено заказов: " + totalOrdersServed);
        System.out.println("Текущая очередь на кухне: " + kitchenQueue.size());
        System.out.println("Готовых заказов на выдаче: " + completedOrders.size());

        System.out.println("\nСтатистика официантов:");
        int totalServed = 0;
        for (Waiter waiter : waiters) {
            int served = waiter.getServedCount();
            totalServed += served;
            System.out.printf("  %s: %d клиентов%n", waiter.getWaiterName(), served);
        }
        System.out.println("Всего обслужено клиентов: " + totalServed);

        System.out.println("\nАктивные повара: " + chefNames);
    }

    public void printMenu() {
        System.out.println("\n МЕНЮ РЕСТОРАНА 'YULIA' ");
        System.out.println("№  Блюдо                     Время приготовления ");

        for (int i = 0; i < menu.size(); i++) {
            String dish = menu.get(i);
            Integer time = Chef.COOKING_TIMES.get(dish);
            int timeMs = time != null ? time : 2000;
            System.out.printf("%2d. %-25s %d мс%n",
                    i + 1, dish, timeMs);
        }
    }

    public void printCurrentStatus() {
        System.out.println("\n ТЕКУЩИЙ СТАТУС РЕСТОРАНА ");
        System.out.println("Статус: " + (isRunning ? "РАБОТАЕТ" : "ЗАКРЫТ"));
        System.out.println("Очередь на кухне: " + kitchenQueue.size() + " заказов");
        System.out.println("Готовые заказы: " + completedOrders.size());
        System.out.println("Активные официанты: " + waiters.size());
        System.out.println("Работает поваров: " + chefNames.size());

        if (!kitchenQueue.isEmpty()) {
            System.out.println("\nЗаказы в очереди на кухне:");
            kitchenQueue.forEach(order ->
                    System.out.println("  " + order));
        }

        if (!completedOrders.isEmpty()) {
            System.out.println("\nГотовые заказы:");
            completedOrders.forEach(order ->
                    System.out.println("  " + order));
        }
    }

    public void addNewDish() {
        try {
            scanner.nextLine(); // Очистка буфера от символа новой строки

            System.out.print("\nВведите название нового блюда: ");
            String dishName = scanner.nextLine();

            if (dishName.trim().isEmpty()) {
                System.out.println("Название блюда не может быть пустым!");
                return;
            }

            System.out.print("Введите время приготовления (в миллисекундах): ");
            int cookingTime = scanner.nextInt();

            if (cookingTime <= 0) {
                System.out.println("Время приготовления должно быть положительным!");
                return;
            }

            // официанты читают menu во время модификации
            menu.add(dishName);
            Chef.COOKING_TIMES.put(dishName, cookingTime);

            System.out.println("Блюдо '" + dishName + "' добавлено в меню!");

            // Важная очистка: nextInt() оставляет \n в буфере
            scanner.nextLine();

        } catch (InputMismatchException e) {
            System.out.println("Ошибка: введите корректное число для времени приготовления!");
            scanner.nextLine(); // Очистка некорректного ввода
        } catch (Exception e) {
            System.out.println("Произошла ошибка: " + e.getMessage());
        }
    }

    public void showDetailedInfo() {
        System.out.println("\n ДЕТАЛЬНАЯ ИНФОРМАЦИЯ ");
        System.out.println("Размер очереди кухни: " + kitchenQueue.size() + "/50");
        System.out.println("Загрузка кухни: " + (kitchenQueue.size() * 100 / 50) + "%");
        System.out.println("Всего потоков поваров: 4");
        System.out.println("Меню содержит: " + menu.size() + " блюд");

        System.out.println("\nИнформация о системе:");
        System.out.println("Доступные процессоры: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Используемая память: " +
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024 + " MB");
    }

    public void showMainMenu() {
        boolean exit = false;

        System.out.println("СИСТЕМА УПРАВЛЕНИЯ РЕСТОРАНОМ ");

        while (!exit) {
            System.out.println("\n ГЛАВНОЕ МЕНЮ ");
            System.out.println("1. Показать меню ресторана");
            System.out.println("2. Запустить работу ресторана");
            System.out.println("3. Остановить работу ресторана");
            System.out.println("4. Показать текущий статус");
            System.out.println("5. Добавить новое блюдо в меню");
            System.out.println("6. Детальная информация");
            System.out.println("7. Тест производительности");
            System.out.println("0. Выход");
            System.out.print("Выберите действие: ");

            try {
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) {
                    continue;
                }

                int choice = Integer.parseInt(input);

                switch (choice) {
                    case 1:
                        printMenu();
                        break;

                    case 2:
                        start();
                        System.out.println(" Ресторан запущен. Работает в фоновом режиме...");
                        break;

                    case 3:
                        stop();
                        break;

                    case 4:
                        printCurrentStatus();
                        break;

                    case 5:
                        addNewDish();
                        break;

                    case 6:
                        showDetailedInfo();
                        break;

                    case 7:
                        runPerformanceTest();
                        break;

                    case 0:
                        exit = true;
                        if (isRunning) {
                            stop();
                        }
                        System.out.println("Выход из системы...");
                        break;

                    default:
                        System.out.println(" Неверный выбор! Пожалуйста, выберите от 0 до 7.");
                }

            } catch (NumberFormatException e) {
                System.out.println("Ошибка: Пожалуйста, введите число!");
            } catch (InterruptedException e) {
                System.out.println("Операция прервана!");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.out.println("Произошла ошибка: " + e.getMessage());
            }
        }

        scanner.close();
        System.out.println("Программа завершена. До свидания!");
    }

    private void runPerformanceTest() throws InterruptedException {
        System.out.println("\n ТЕСТ ПРОИЗВОДИТЕЛЬНОСТИ ");
        System.out.println("Запуск теста на 30 секунд...");

        long startTime = System.currentTimeMillis();
        start();
        Thread.sleep(30000);
        stop();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("\nРезультаты теста:");
        System.out.println("Время работы: " + duration + " мс");
        System.out.println("Обработано заказов: " + totalOrdersServed);
        System.out.println("Средняя скорость: " +
                String.format("%.2f", (totalOrdersServed * 1000.0 / duration)) + " заказов/сек");

        totalOrdersServed = 0;  // Сброс счетчика для следующего теста
    }

    public static void main(String[] args) {
        RestaurantManagementSystem restaurant = new RestaurantManagementSystem();
        restaurant.showMainMenu();
    }
}