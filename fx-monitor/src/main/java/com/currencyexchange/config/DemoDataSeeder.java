package com.currencyexchange.config;

import com.currencyexchange.entity.Exposure;
import com.currencyexchange.entity.Transaction;
import com.currencyexchange.entity.User;
import com.currencyexchange.entity.Wallet;
import com.currencyexchange.repository.ExposureRepository;
import com.currencyexchange.repository.TransactionRepository;
import com.currencyexchange.repository.UserRepository;
import com.currencyexchange.repository.WalletRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Seeds a ready-to-demo account (user + wallets + transaction history + FX
 * exposures) on startup.
 *
 * <p>Active only under the {@code demo} Spring profile so it never touches a real
 * database during normal runs or tests. Launch with the {@code demo} profile to
 * populate the embedded H2 file DB, then log in with the credentials below.
 *
 * <p>Idempotent: if the demo user already exists the seeder does nothing, so the
 * app can be launched repeatedly without duplicating data.
 *
 * <pre>
 *   Email:    demo@fxmonitor.com
 *   Password: demo1234
 * </pre>
 */
@Component
@Profile("demo")
@Slf4j
public class DemoDataSeeder implements CommandLineRunner {

    private static final String DEMO_EMAIL = "demo@fxmonitor.com";
    private static final String DEMO_PASSWORD = "demo1234";

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final ExposureRepository exposureRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(UserRepository userRepository,
                          WalletRepository walletRepository,
                          TransactionRepository transactionRepository,
                          ExposureRepository exposureRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.exposureRepository = exposureRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByEmail(DEMO_EMAIL)) {
            log.info("Demo data already present for {} — skipping seed.", DEMO_EMAIL);
            return;
        }

        User user = seedUser();
        Wallet usd = seedWallet(user, "USD", "23500.00", "2000.00");
        Wallet eur = seedWallet(user, "EUR", "18400.50", "0.00");
        Wallet gbp = seedWallet(user, "GBP", "9750.00", "0.00");
        Wallet jpy = seedWallet(user, "JPY", "1485000.00", "0.00");
        Wallet cny = seedWallet(user, "CNY", "58000.00", "0.00");

        // Transaction history, oldest first. Dates are backdated to read like real activity.
        deposit(user, usd, "30000.00", 42, "Initial funding from external bank");
        exchange(user, usd, eur, "10000.00", "9200.00", "0.920000", "50.00", 36, "USD → EUR conversion");
        exchange(user, usd, gbp, "12000.00", "9450.00", "0.787500", "60.00", 29, "USD → GBP conversion");
        deposit(user, jpy, "1500000.00", 22, "JPY operating-account top-up");
        exchange(user, usd, cny, "8000.00", "58000.00", "7.250000", "40.00", 18, "USD → CNY conversion");
        withdrawal(user, usd, "5000.00", "10.00", 12, "Withdrawal to settlement account");
        transfer(user, jpy, "15000.00", "5.00", 7, "Transfer to partner account");
        exchange(user, gbp, eur, "2000.00", "2340.00", "1.170000", "11.00", 4, "GBP → EUR rebalancing");
        pendingExchange(user, usd, eur, "3000.00", "2760.00", "0.920000", "15.00", 1, "USD → EUR (settling)");

        // FX exposures: the treasury positions that carry currency risk. A mix of
        // receivables/payables/cash/forecast/translation across EUR, GBP and JPY so
        // the net-exposure netting and the Exposures tab have something to show.
        seedExposure(user, Exposure.TYPE_RECEIVABLE, "EUR", "60000.00",
                "Müller GmbH", "DE Subsidiary", 40, Exposure.STATUS_OPEN, "AR — invoice #4821");
        seedExposure(user, Exposure.TYPE_PAYABLE, "EUR", "22000.00",
                "Bosch Zulieferer", "DE Subsidiary", 25, Exposure.STATUS_OPEN, "AP — component supply");
        seedExposure(user, Exposure.TYPE_CASH, "EUR", "40000.00",
                "Frankfurt operating account", "DE Subsidiary", null, Exposure.STATUS_OPEN, "EUR cash on hand");
        seedExposure(user, Exposure.TYPE_TRANSLATION, "EUR", "25000.00",
                null, "DE Subsidiary", null, Exposure.STATUS_OPEN, "Subsidiary equity translation");
        seedExposure(user, Exposure.TYPE_RECEIVABLE, "GBP", "35000.00",
                "London Partners Ltd", "UK Branch", 55, Exposure.STATUS_OPEN, "AR — services Q3");
        seedExposure(user, Exposure.TYPE_PAYABLE, "GBP", "8000.00",
                "UK Logistics", "UK Branch", 18, Exposure.STATUS_OPEN, "AP — freight");
        seedExposure(user, Exposure.TYPE_FORECAST, "JPY", "5000000.00",
                "Forecast JP sales", "JP Branch", 90, Exposure.STATUS_OPEN, "Q4 forecast inflow");
        seedExposure(user, Exposure.TYPE_RECEIVABLE, "EUR", "18000.00",
                "Adler AG", "DE Subsidiary", -5, Exposure.STATUS_SETTLED, "AR — settled invoice #4770");

        log.info("Seeded demo account {} with {} wallets, a transaction history, and FX exposures.",
                DEMO_EMAIL, 5);
        log.info("Log in with  {} / {}", DEMO_EMAIL, DEMO_PASSWORD);
    }

    private User seedUser() {
        User user = new User();
        user.setEmail(DEMO_EMAIL);
        user.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
        user.setFullName("Demo Trader");
        user.setPhoneNumber("+1 555 0100");
        user.setCountry("United States");
        user.setKycStatus("APPROVED");
        user.setDailyExchangeLimit(BigDecimal.valueOf(50000));
        user.setIsActive(true);
        user.setIsEmailVerified(true);
        return userRepository.save(user);
    }

    private Wallet seedWallet(User user, String currency, String balance, String reserved) {
        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet.setCurrency(currency);
        wallet.setWalletAddress("DEMO-" + currency + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        wallet.setBalance(new BigDecimal(balance));
        wallet.setReservedAmount(new BigDecimal(reserved));
        return walletRepository.save(wallet);
    }

    /**
     * Books a demo exposure. The amount is always positive; the type decides
     * whether it nets long or short. {@code maturityInDays} may be null for
     * positions without a settlement date (cash, translation); a negative value
     * backdates the maturity so a SETTLED position reads as already matured.
     */
    private void seedExposure(User user, String type, String currency, String amount,
                              String counterparty, String entityName, Integer maturityInDays,
                              String status, String description) {
        Exposure exposure = new Exposure();
        exposure.setUser(user);
        exposure.setType(type);
        exposure.setCurrency(currency);
        exposure.setAmount(new BigDecimal(amount));
        exposure.setCounterparty(counterparty);
        exposure.setEntityName(entityName);
        exposure.setValueDate(LocalDate.now().minusDays(10));
        exposure.setMaturityDate(maturityInDays != null ? LocalDate.now().plusDays(maturityInDays) : null);
        exposure.setStatus(status);
        exposure.setDescription(description);
        exposureRepository.save(exposure);
    }

    private void deposit(User user, Wallet to, String amount, int daysAgo, String description) {
        Transaction t = baseTransaction(user, "DEPOSIT", description, daysAgo);
        t.setToWallet(to);
        t.setFromAmount(new BigDecimal(amount));
        t.setToAmount(new BigDecimal(amount));
        saveCompleted(t, daysAgo);
    }

    private void withdrawal(User user, Wallet from, String amount, String fee, int daysAgo, String description) {
        Transaction t = baseTransaction(user, "WITHDRAWAL", description, daysAgo);
        t.setFromWallet(from);
        t.setFromAmount(new BigDecimal(amount));
        t.setToAmount(new BigDecimal(amount));
        t.setFeeAmount(new BigDecimal(fee));
        saveCompleted(t, daysAgo);
    }

    private void transfer(User user, Wallet from, String amount, String fee, int daysAgo, String description) {
        Transaction t = baseTransaction(user, "TRANSFER", description, daysAgo);
        t.setFromWallet(from);
        t.setFromAmount(new BigDecimal(amount));
        t.setToAmount(new BigDecimal(amount));
        t.setFeeAmount(new BigDecimal(fee));
        saveCompleted(t, daysAgo);
    }

    private void exchange(User user, Wallet from, Wallet to, String fromAmount, String toAmount,
                          String rate, String fee, int daysAgo, String description) {
        Transaction t = baseTransaction(user, "EXCHANGE", description, daysAgo);
        t.setFromWallet(from);
        t.setToWallet(to);
        t.setFromAmount(new BigDecimal(fromAmount));
        t.setToAmount(new BigDecimal(toAmount));
        t.setExchangeRateUsed(new BigDecimal(rate));
        t.setFeeAmount(new BigDecimal(fee));
        saveCompleted(t, daysAgo);
    }

    private void pendingExchange(User user, Wallet from, Wallet to, String fromAmount, String toAmount,
                                 String rate, String fee, int daysAgo, String description) {
        Transaction t = baseTransaction(user, "EXCHANGE", description, daysAgo);
        t.setFromWallet(from);
        t.setToWallet(to);
        t.setFromAmount(new BigDecimal(fromAmount));
        t.setToAmount(new BigDecimal(toAmount));
        t.setExchangeRateUsed(new BigDecimal(rate));
        t.setFeeAmount(new BigDecimal(fee));
        t.setStatus("PENDING");
        // Persist (@PrePersist stamps createdAt = now), then backdate so it sorts naturally.
        Transaction saved = transactionRepository.save(t);
        saved.setCreatedAt(LocalDateTime.now().minusDays(daysAgo));
        transactionRepository.save(saved);
    }

    private final AtomicInteger refSeq = new AtomicInteger();

    private Transaction baseTransaction(User user, String type, String description, int daysAgo) {
        Transaction t = new Transaction();
        t.setUser(user);
        t.setTransactionType(type);
        t.setDescription(description);
        t.setFeeAmount(BigDecimal.ZERO);
        t.setStatus("PENDING");
        t.setTransactionReference("TXN-DEMO" + String.format("%010d", refSeq.incrementAndGet()));
        return t;
    }

    /**
     * Marks a transaction COMPLETED and backdates it. {@code @PrePersist} stamps
     * {@code createdAt} to "now" on insert, so we set the historical timestamps
     * after the initial save and persist again.
     */
    private void saveCompleted(Transaction t, int daysAgo) {
        t.setStatus("COMPLETED");
        Transaction saved = transactionRepository.save(t);
        LocalDateTime when = LocalDateTime.now().minusDays(daysAgo);
        saved.setCreatedAt(when);
        saved.setCompletedAt(when.plusMinutes(2));
        transactionRepository.save(saved);
    }
}
