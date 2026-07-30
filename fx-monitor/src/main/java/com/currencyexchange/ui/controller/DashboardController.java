package com.currencyexchange.ui.controller;

import com.currencyexchange.dto.auth.AuthResponseDTO;
import com.currencyexchange.dto.exchange.ExchangeRateDTO;
import com.currencyexchange.dto.statistics.CurrencyExposureDTO;
import com.currencyexchange.dto.statistics.PortfolioStatisticsDTO;
import com.currencyexchange.dto.transactions.TransactionDTO;
import com.currencyexchange.entity.Wallet;
import com.currencyexchange.repository.WalletRepository;
import com.currencyexchange.service.AuthService;
import com.currencyexchange.service.ExchangeRateService;
import com.currencyexchange.service.PortfolioStatisticsService;
import com.currencyexchange.service.TransactionService;
import com.currencyexchange.ui.util.SceneNavigator;
import com.currencyexchange.ui.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DashboardController {

    private static final String[] WATCHED_CURRENCIES = {"EUR", "USD", "JPY", "GBP", "CNY"};

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private static final String HOME_CURRENCY = "USD";

    @Autowired private WalletRepository walletRepository;
    @Autowired private AuthService authService;
    @Autowired private ExchangeRateService exchangeRateService;
    @Autowired private TransactionService transactionService;
    @Autowired private PortfolioStatisticsService portfolioStatisticsService;
    @Autowired private ApplicationContext applicationContext;

    @FXML private Label userNameLabel;
    @FXML private Label userEmailLabel;
    @FXML private Label pageTitle;
    @FXML private VBox contentArea;
    @FXML private FlowPane ratesPane;

    @FXML
    public void initialize() {
        AuthResponseDTO session = SessionManager.getSession();
        if (session != null) {
            userNameLabel.setText(session.getFullName());
            userEmailLabel.setText(session.getEmail());
        }
        showPositions();
        loadExchangeRates();
    }

    private void loadExchangeRates() {
        Label loading = new Label("Loading rates...");
        loading.getStyleClass().add("muted-text");
        ratesPane.getChildren().setAll(loading);

        Thread t = new Thread(() -> {
            try {
                ExchangeRateDTO rates = exchangeRateService.getRates("USD");
                Platform.runLater(() -> displayRates(rates));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    Label err = new Label("Rates unavailable");
                    err.getStyleClass().add("muted-text");
                    ratesPane.getChildren().setAll(err);
                });
            }
        }, "rate-loader");
        t.setDaemon(true);
        t.start();
    }

    private void displayRates(ExchangeRateDTO rates) {
        ratesPane.getChildren().clear();
        for (String currency : WATCHED_CURRENCIES) {
            BigDecimal rate = rates.getRates().get(currency);
            if (rate != null) {
                ratesPane.getChildren().add(buildRateCard(currency, rate));
            }
        }
    }

    private VBox buildRateCard(String currency, BigDecimal rate) {
        VBox card = new VBox(3);
        card.getStyleClass().add("rate-card");

        Label currencyLabel = new Label(currency);
        currencyLabel.getStyleClass().add("rate-currency");

        Label rateLabel = new Label();
        rateLabel.getStyleClass().add("rate-value");

        Label descLabel = new Label();
        descLabel.getStyleClass().add("rate-description");

        if ("USD".equals(currency)) {
            rateLabel.setText("BASE");
            descLabel.setText("reference");
        } else {
            int scale = "JPY".equals(currency) ? 2 : 4;
            rateLabel.setText(rate.setScale(scale, RoundingMode.HALF_UP).toPlainString());
            descLabel.setText("per 1 USD");
        }

        card.getChildren().addAll(currencyLabel, rateLabel, descLabel);
        return card;
    }

    @FXML
    private void showPositions() {
        pageTitle.setText("My Positions");
        contentArea.getChildren().clear();

        AuthResponseDTO session = SessionManager.getSession();
        if (session == null) return;

        List<Wallet> wallets = walletRepository.findByUserId(session.getUserId());

        if (wallets.isEmpty()) {
            Label empty = new Label("No positions yet. Positions will appear here once created.");
            empty.getStyleClass().add("muted-text");
            contentArea.getChildren().add(empty);
            return;
        }

        FlowPane cards = new FlowPane();
        cards.setHgap(16);
        cards.setVgap(16);
        for (Wallet wallet : wallets) {
            cards.getChildren().add(buildPositionCard(wallet));
        }
        contentArea.getChildren().add(cards);
    }

    @FXML
    private void showTransactions() {
        pageTitle.setText("Transactions");
        contentArea.getChildren().clear();

        AuthResponseDTO session = SessionManager.getSession();
        if (session == null) return;

        Label loading = new Label("Loading transactions...");
        loading.getStyleClass().add("muted-text");
        contentArea.getChildren().add(loading);

        Long userId = session.getUserId();
        Thread t = new Thread(() -> {
            try {
                Map<Long, String> walletCurrencies = walletRepository.findByUserId(userId).stream()
                        .collect(Collectors.toMap(Wallet::getId, Wallet::getCurrency));
                List<TransactionDTO> transactions = transactionService.getUserTransactions(userId);
                Platform.runLater(() -> displayTransactions(transactions, walletCurrencies));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    contentArea.getChildren().clear();
                    Label err = new Label("Transactions unavailable.");
                    err.getStyleClass().add("muted-text");
                    contentArea.getChildren().add(err);
                });
            }
        }, "transaction-loader");
        t.setDaemon(true);
        t.start();
    }

    private void displayTransactions(List<TransactionDTO> transactions, Map<Long, String> walletCurrencies) {
        contentArea.getChildren().clear();

        if (transactions.isEmpty()) {
            Label empty = new Label("No transactions yet. Your activity will appear here.");
            empty.getStyleClass().add("muted-text");
            contentArea.getChildren().add(empty);
            return;
        }

        VBox table = new VBox();
        table.getStyleClass().add("txn-table");
        table.getChildren().add(buildTransactionHeader());
        for (TransactionDTO txn : transactions) {
            table.getChildren().add(buildTransactionRow(txn, walletCurrencies));
        }
        contentArea.getChildren().add(table);
    }

    private HBox buildTransactionHeader() {
        HBox header = new HBox();
        header.getStyleClass().add("txn-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(
                headerCell("DATE", 150),
                headerCell("TYPE", 110),
                headerCell("REFERENCE", 180),
                headerCell("AMOUNT", 0),
                headerCell("STATUS", 110));
        return header;
    }

    private Label headerCell(String text, double width) {
        Label label = new Label(text);
        label.getStyleClass().add("txn-header-cell");
        if (width > 0) {
            label.setMinWidth(width);
            label.setPrefWidth(width);
        } else {
            label.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(label, Priority.ALWAYS);
        }
        return label;
    }

    private HBox buildTransactionRow(TransactionDTO txn, Map<Long, String> walletCurrencies) {
        HBox row = new HBox();
        row.getStyleClass().add("txn-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label date = new Label(txn.getCreatedAt() != null
                ? txn.getCreatedAt().format(DATE_FORMAT) : "—");
        date.getStyleClass().add("txn-cell");
        date.setMinWidth(150);
        date.setPrefWidth(150);

        Label type = new Label(txn.getTransactionType());
        type.getStyleClass().addAll("txn-badge", "txn-type-" + txn.getTransactionType().toLowerCase());
        HBox typeBox = new HBox(type);
        typeBox.setMinWidth(110);
        typeBox.setPrefWidth(110);
        typeBox.setAlignment(Pos.CENTER_LEFT);

        Label reference = new Label(txn.getTransactionReference() != null
                ? txn.getTransactionReference() : "—");
        reference.getStyleClass().add("txn-cell-muted");
        reference.setMinWidth(180);
        reference.setPrefWidth(180);

        Label amount = new Label(formatAmount(txn, walletCurrencies));
        amount.getStyleClass().add("txn-cell");
        amount.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(amount, Priority.ALWAYS);

        Label status = new Label(txn.getStatus());
        status.getStyleClass().addAll("txn-badge", "txn-status-" + txn.getStatus().toLowerCase());
        HBox statusBox = new HBox(status);
        statusBox.setMinWidth(110);
        statusBox.setPrefWidth(110);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        row.getChildren().addAll(date, typeBox, reference, amount, statusBox);
        return row;
    }

    private String formatAmount(TransactionDTO txn, Map<Long, String> walletCurrencies) {
        String fromCcy = walletCurrencies.get(txn.getFromWalletId());
        String toCcy = walletCurrencies.get(txn.getToWalletId());

        String from = txn.getFromAmount() != null
                ? txn.getFromAmount().toPlainString() + (fromCcy != null ? " " + fromCcy : "") : null;
        String to = txn.getToAmount() != null
                ? txn.getToAmount().toPlainString() + (toCcy != null ? " " + toCcy : "") : null;

        if (from != null && to != null) {
            return from + "  →  " + to;
        }
        return from != null ? from : (to != null ? to : "—");
    }

    @FXML
    private void showStatistics() {
        pageTitle.setText("Statistics");
        contentArea.getChildren().clear();

        AuthResponseDTO session = SessionManager.getSession();
        if (session == null) return;

        Label loading = new Label("Calculating portfolio statistics...");
        loading.getStyleClass().add("muted-text");
        contentArea.getChildren().add(loading);

        Long userId = session.getUserId();
        Thread t = new Thread(() -> {
            try {
                PortfolioStatisticsDTO stats =
                        portfolioStatisticsService.getPortfolioStatistics(userId, HOME_CURRENCY);
                Platform.runLater(() -> displayStatistics(stats));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    contentArea.getChildren().clear();
                    Label err = new Label("Statistics unavailable — exchange rates could not be loaded.");
                    err.getStyleClass().add("muted-text");
                    contentArea.getChildren().add(err);
                });
            }
        }, "statistics-loader");
        t.setDaemon(true);
        t.start();
    }

    private void displayStatistics(PortfolioStatisticsDTO stats) {
        contentArea.getChildren().clear();

        if (stats.getExposures().isEmpty()) {
            Label empty = new Label("No holdings yet. Portfolio statistics will appear once you have wallets.");
            empty.getStyleClass().add("muted-text");
            contentArea.getChildren().add(empty);
            return;
        }

        contentArea.getChildren().add(buildTotalValueCard(stats));

        VBox table = new VBox();
        table.getStyleClass().add("txn-table");
        table.getChildren().add(buildExposureHeader());
        for (CurrencyExposureDTO exposure : stats.getExposures()) {
            table.getChildren().add(buildExposureRow(exposure, stats.getHomeCurrency()));
        }
        contentArea.getChildren().add(table);

        if (!stats.getUnvaluedCurrencies().isEmpty()) {
            Label note = new Label("No rate available for: "
                    + String.join(", ", stats.getUnvaluedCurrencies())
                    + " — excluded from total value.");
            note.getStyleClass().add("muted-text");
            note.setWrapText(true);
            contentArea.getChildren().add(note);
        }
    }

    private VBox buildTotalValueCard(PortfolioStatisticsDTO stats) {
        VBox card = new VBox(4);
        card.getStyleClass().add("stat-summary-card");
        card.setPadding(new Insets(20));

        Label caption = new Label("TOTAL PORTFOLIO VALUE");
        caption.getStyleClass().add("stat-summary-caption");

        Label value = new Label(formatMoney(stats.getTotalValueInHome()) + " " + stats.getHomeCurrency());
        value.getStyleClass().add("stat-summary-value");

        Label sub = new Label(stats.getCurrencyCount() + " "
                + (stats.getCurrencyCount() == 1 ? "currency" : "currencies") + " held");
        sub.getStyleClass().add("stat-summary-sub");

        card.getChildren().addAll(caption, value, sub);
        return card;
    }

    private HBox buildExposureHeader() {
        HBox header = new HBox();
        header.getStyleClass().add("txn-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(
                headerCell("CURRENCY", 110),
                headerCell("NET POSITION", 170),
                headerCell("RATE", 130),
                headerCell("VALUE (" + HOME_CURRENCY + ")", 0),
                headerCell("SHARE", 90));
        return header;
    }

    private HBox buildExposureRow(CurrencyExposureDTO exposure, String homeCurrency) {
        HBox row = new HBox();
        row.getStyleClass().add("txn-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label currency = new Label(exposure.getCurrency());
        currency.getStyleClass().add("txn-cell");
        currency.setMinWidth(110);
        currency.setPrefWidth(110);

        Label position = new Label(formatMoney(exposure.getNetExposure()) + " " + exposure.getCurrency());
        position.getStyleClass().add("txn-cell");
        position.setMinWidth(170);
        position.setPrefWidth(170);

        Label rate = new Label(exposure.getRateToHome() != null
                ? exposure.getRateToHome().toPlainString() : "—");
        rate.getStyleClass().add("txn-cell-muted");
        rate.setMinWidth(130);
        rate.setPrefWidth(130);

        Label value = new Label(exposure.getValueInHome() != null
                ? formatMoney(exposure.getValueInHome()) + " " + homeCurrency : "—");
        value.getStyleClass().add("txn-cell");
        value.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(value, Priority.ALWAYS);

        Label share = new Label(exposure.getPercentOfPortfolio() != null
                ? exposure.getPercentOfPortfolio().toPlainString() + "%" : "—");
        share.getStyleClass().add("txn-cell-muted");
        share.setMinWidth(90);
        share.setPrefWidth(90);

        row.getChildren().addAll(currency, position, rate, value, share);
        return row;
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        return String.format("%,.2f", amount.setScale(2, RoundingMode.HALF_UP));
    }

    @FXML
    private void showProfile() {
        pageTitle.setText("Profile");
        contentArea.getChildren().clear();

        AuthResponseDTO session = SessionManager.getSession();
        if (session == null) return;

        VBox card = new VBox(12);
        card.getStyleClass().add("profile-card");

        Label name = new Label("Name:   " + session.getFullName());
        name.getStyleClass().add("body-text");
        Label email = new Label("Email:  " + session.getEmail());
        email.getStyleClass().add("body-text");

        card.getChildren().addAll(name, email);
        contentArea.getChildren().add(card);
    }

    @FXML
    private void handleLogout() {
        try {
            AuthResponseDTO session = SessionManager.getSession();
            SessionManager.clearSession();
            if (session != null) {
                authService.logout(session.getUserId());
            }
            SceneNavigator.navigate(userNameLabel, "/fxml/login.fxml",
                    420, 520, applicationContext);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private VBox buildPositionCard(Wallet wallet) {
        VBox card = new VBox(8);
        card.getStyleClass().add("wallet-card");
        card.setPrefWidth(200);
        card.setPadding(new Insets(20));

        Label currency = new Label(wallet.getCurrency());
        currency.getStyleClass().add("wallet-currency");

        Label balance = new Label(wallet.getBalance().toPlainString());
        balance.getStyleClass().add("wallet-balance");

        Label available = new Label("Available: " + wallet.getAvailableBalance().toPlainString());
        available.getStyleClass().add("wallet-available");

        card.getChildren().addAll(currency, balance, available);
        return card;
    }
}
