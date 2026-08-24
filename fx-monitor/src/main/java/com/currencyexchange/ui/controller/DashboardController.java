package com.currencyexchange.ui.controller;

import com.currencyexchange.dto.alerts.AlertDTO;
import com.currencyexchange.dto.alerts.CreateAlertRequestDTO;
import com.currencyexchange.dto.analytics.AttributionResultDTO;
import com.currencyexchange.dto.analytics.CurrencyAttributionDTO;
import com.currencyexchange.dto.analytics.ScenarioResultDTO;
import com.currencyexchange.dto.analytics.StressTestResultDTO;
import com.currencyexchange.dto.analytics.VarResultDTO;
import com.currencyexchange.dto.auth.AuthResponseDTO;
import com.currencyexchange.dto.exchange.ExchangeRateDTO;
import com.currencyexchange.dto.exposures.CreateExposureRequestDTO;
import com.currencyexchange.dto.exposures.ExposureDTO;
import com.currencyexchange.dto.hedges.CreateHedgeRequestDTO;
import com.currencyexchange.dto.hedges.HedgeDTO;
import com.currencyexchange.dto.statistics.CurrencyExposureDTO;
import com.currencyexchange.dto.statistics.PortfolioStatisticsDTO;
import com.currencyexchange.dto.transactions.CreateTransactionRequestDTO;
import com.currencyexchange.dto.transactions.TransactionDTO;
import com.currencyexchange.entity.Exposure;
import com.currencyexchange.entity.Hedge;
import com.currencyexchange.entity.RateAlert;
import com.currencyexchange.entity.Wallet;
import com.currencyexchange.repository.WalletRepository;
import com.currencyexchange.service.AuthService;
import com.currencyexchange.service.ExchangeRateService;
import com.currencyexchange.service.ExposureService;
import com.currencyexchange.service.HedgeService;
import com.currencyexchange.service.PortfolioStatisticsService;
import com.currencyexchange.service.RateAlertService;
import com.currencyexchange.service.RiskMetricsService;
import com.currencyexchange.service.ScenarioAnalysisService;
import com.currencyexchange.service.TransactionService;
import com.currencyexchange.ui.util.SceneNavigator;
import com.currencyexchange.ui.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DashboardController {

    private static final String[] WATCHED_CURRENCIES = {"EUR", "USD", "JPY", "GBP", "CNY"};

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private static final DateTimeFormatter DATE_ONLY_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final String HOME_CURRENCY = "USD";

    @Autowired private WalletRepository walletRepository;
    @Autowired private AuthService authService;
    @Autowired private ExchangeRateService exchangeRateService;
    @Autowired private TransactionService transactionService;
    @Autowired private ExposureService exposureService;
    @Autowired private PortfolioStatisticsService portfolioStatisticsService;
    @Autowired private HedgeService hedgeService;
    @Autowired private RateAlertService rateAlertService;
    @Autowired private ScenarioAnalysisService scenarioAnalysisService;
    @Autowired private RiskMetricsService riskMetricsService;
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
        showWallets();
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
    private void showWallets() {
        pageTitle.setText("My Wallets");
        contentArea.getChildren().clear();

        AuthResponseDTO session = SessionManager.getSession();
        if (session == null) return;

        List<Wallet> wallets = walletRepository.findByUserId(session.getUserId());

        if (wallets.isEmpty()) {
            Label empty = new Label("No wallets yet. Wallets will appear here once created.");
            empty.getStyleClass().add("muted-text");
            contentArea.getChildren().add(empty);
            return;
        }

        FlowPane cards = new FlowPane();
        cards.setHgap(16);
        cards.setVgap(16);
        for (Wallet wallet : wallets) {
            cards.getChildren().add(buildWalletCard(wallet));
        }
        contentArea.getChildren().add(cards);
    }

    @FXML
    private void showExposures() {
        pageTitle.setText("Exposures");
        contentArea.getChildren().clear();

        AuthResponseDTO session = SessionManager.getSession();
        if (session == null) return;

        Label loading = new Label("Loading exposures...");
        loading.getStyleClass().add("muted-text");
        contentArea.getChildren().addAll(buildExposuresActionBar(), loading);

        Long userId = session.getUserId();
        Thread t = new Thread(() -> {
            try {
                List<ExposureDTO> exposures = exposureService.getUserExposures(userId);
                Platform.runLater(() -> displayExposures(exposures));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    contentArea.getChildren().clear();
                    contentArea.getChildren().add(buildExposuresActionBar());
                    Label err = new Label("Exposures unavailable.");
                    err.getStyleClass().add("muted-text");
                    contentArea.getChildren().add(err);
                });
            }
        }, "exposure-loader");
        t.setDaemon(true);
        t.start();
    }

    private void displayExposures(List<ExposureDTO> exposures) {
        contentArea.getChildren().clear();
        contentArea.getChildren().add(buildExposuresActionBar());

        if (exposures.isEmpty()) {
            Label empty = new Label("No exposures yet. Add a receivable, payable, or cash position to get started.");
            empty.getStyleClass().add("muted-text");
            contentArea.getChildren().add(empty);
            return;
        }

        VBox table = new VBox();
        table.getStyleClass().add("txn-table");
        table.getChildren().add(buildExposureListHeader());
        for (ExposureDTO exposure : exposures) {
            table.getChildren().add(buildExposureListRow(exposure));
        }
        contentArea.getChildren().add(table);
    }

    private HBox buildExposuresActionBar() {
        Button add = new Button("+ New Exposure");
        add.getStyleClass().add("action-button");
        add.setOnAction(e -> openCreateExposureDialog());

        HBox bar = new HBox(add);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private HBox buildExposureListHeader() {
        HBox header = new HBox();
        header.getStyleClass().add("txn-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(
                headerCell("TYPE", 130),
                headerCell("CURRENCY", 90),
                headerCell("POSITION", 0),
                headerCell("COUNTERPARTY", 180),
                headerCell("MATURITY", 120),
                headerCell("STATUS", 110));
        return header;
    }

    private HBox buildExposureListRow(ExposureDTO exposure) {
        HBox row = new HBox();
        row.getStyleClass().add("txn-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label type = new Label(exposure.getType());
        type.getStyleClass().add("txn-badge");
        HBox typeBox = new HBox(type);
        typeBox.setMinWidth(130);
        typeBox.setPrefWidth(130);
        typeBox.setAlignment(Pos.CENTER_LEFT);

        Label currency = new Label(exposure.getCurrency());
        currency.getStyleClass().add("txn-cell");
        currency.setMinWidth(90);
        currency.setPrefWidth(90);

        BigDecimal signed = exposure.getSignedAmount() != null
                ? exposure.getSignedAmount() : exposure.getAmount();
        Label position = new Label(formatMoney(signed) + " " + exposure.getCurrency());
        position.getStyleClass().add("txn-cell");
        position.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(position, Priority.ALWAYS);

        Label counterparty = new Label(exposure.getCounterparty() != null
                ? exposure.getCounterparty() : "—");
        counterparty.getStyleClass().add("txn-cell-muted");
        counterparty.setMinWidth(180);
        counterparty.setPrefWidth(180);

        Label maturity = new Label(exposure.getMaturityDate() != null
                ? exposure.getMaturityDate().format(DATE_ONLY_FORMAT) : "—");
        maturity.getStyleClass().add("txn-cell-muted");
        maturity.setMinWidth(120);
        maturity.setPrefWidth(120);

        Label status = new Label(exposure.getStatus());
        status.getStyleClass().addAll("txn-badge", "txn-status-" + exposure.getStatus().toLowerCase());
        HBox statusBox = new HBox(status);
        statusBox.setMinWidth(110);
        statusBox.setPrefWidth(110);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        row.getChildren().addAll(typeBox, currency, position, counterparty, maturity, statusBox);
        return row;
    }

    /**
     * Opens a modal form for booking an exposure. Amount is always entered as a
     * positive number; the type (receivable, payable, cash, …) determines whether
     * the position nets long or short. On success the dialog closes and the
     * exposures list refreshes.
     */
    private void openCreateExposureDialog() {
        AuthResponseDTO session = SessionManager.getSession();
        if (session == null) return;
        Long userId = session.getUserId();

        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll(
                Exposure.TYPE_RECEIVABLE, Exposure.TYPE_PAYABLE, Exposure.TYPE_CASH,
                Exposure.TYPE_INTERCOMPANY, Exposure.TYPE_FORECAST, Exposure.TYPE_TRANSLATION);
        typeBox.setValue(Exposure.TYPE_RECEIVABLE);
        typeBox.setMaxWidth(Double.MAX_VALUE);

        TextField currency = dialogField("EUR");
        TextField amount = dialogField("0.00");
        TextField counterparty = dialogField("optional");
        TextField entityName = dialogField("optional");
        DatePicker maturity = new DatePicker();
        maturity.setMaxWidth(Double.MAX_VALUE);
        maturity.setPromptText("optional");
        TextField description = dialogField("optional");

        Label error = new Label();
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setManaged(false);
        error.setVisible(false);

        Button submit = new Button("Add Exposure");
        submit.getStyleClass().add("primary-button");
        submit.setMaxWidth(Double.MAX_VALUE);

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("link-text");
        cancel.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label("New Exposure");
        title.getStyleClass().add("dialog-title");

        VBox form = new VBox(12);
        form.getStyleClass().add("dialog-form");
        form.setPrefWidth(360);
        form.getChildren().addAll(
                title,
                labeledControl("TYPE", typeBox),
                labeledControl("CURRENCY", currency),
                labeledControl("AMOUNT", amount),
                labeledControl("COUNTERPARTY", counterparty),
                labeledControl("ENTITY / SUBSIDIARY", entityName),
                labeledControl("MATURITY DATE", maturity),
                labeledControl("DESCRIPTION", description),
                error,
                submit,
                cancel);

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(contentArea.getScene().getWindow());
        dialog.setTitle("New Exposure");

        cancel.setOnAction(e -> dialog.close());

        submit.setOnAction(e -> {
            error.setManaged(false);
            error.setVisible(false);

            String currencyCode = currency.getText().trim();
            if (currencyCode.isEmpty()) {
                showDialogError(error, "Currency is required.");
                return;
            }

            BigDecimal amountVal;
            try {
                amountVal = new BigDecimal(amount.getText().trim());
            } catch (NumberFormatException ex) {
                showDialogError(error, "Amount must be a valid number.");
                return;
            }
            if (amountVal.signum() <= 0) {
                showDialogError(error, "Amount must be greater than zero.");
                return;
            }

            CreateExposureRequestDTO request = new CreateExposureRequestDTO();
            request.setType(typeBox.getValue());
            request.setCurrency(currencyCode);
            request.setAmount(amountVal);
            request.setCounterparty(emptyToNull(counterparty.getText()));
            request.setEntityName(emptyToNull(entityName.getText()));
            LocalDate maturityDate = maturity.getValue();
            request.setMaturityDate(maturityDate);
            request.setValueDate(LocalDate.now());
            request.setDescription(emptyToNull(description.getText()));

            submit.setDisable(true);
            Thread t = new Thread(() -> {
                try {
                    exposureService.createExposure(userId, request);
                    Platform.runLater(() -> {
                        dialog.close();
                        showExposures();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        showDialogError(error, "Could not add exposure: " + ex.getMessage());
                        submit.setDisable(false);
                    });
                }
            }, "create-exposure");
            t.setDaemon(true);
            t.start();
        });

        Scene scene = new Scene(form);
        scene.getStylesheets().add(
                getClass().getResource("/css/styles.css").toExternalForm());
        dialog.setScene(scene);
        dialog.setResizable(false);
        dialog.showAndWait();
    }

    private String emptyToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ===================== Hedges =====================

    @FXML
    private void showHedges() {
        pageTitle.setText("Hedges");
        contentArea.getChildren().clear();

        AuthResponseDTO session = SessionManager.getSession();
        if (session == null) return;

        Label loading = new Label("Loading hedges...");
        loading.getStyleClass().add("muted-text");
        contentArea.getChildren().addAll(buildHedgesActionBar(), loading);

        Long userId = session.getUserId();
        Thread t = new Thread(() -> {
            try {
                List<HedgeDTO> hedges = hedgeService.getUserHedges(userId);
                Platform.runLater(() -> displayHedges(hedges));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    contentArea.getChildren().clear();
                    contentArea.getChildren().add(buildHedgesActionBar());
                    Label err = new Label("Hedges unavailable.");
                    err.getStyleClass().add("muted-text");
                    contentArea.getChildren().add(err);
                });
            }
        }, "hedge-loader");
        t.setDaemon(true);
        t.start();
    }

    private void displayHedges(List<HedgeDTO> hedges) {
        contentArea.getChildren().clear();
        contentArea.getChildren().add(buildHedgesActionBar());

        if (hedges.isEmpty()) {
            Label empty = new Label("No hedges yet. Book a forward or option to cover an exposure.");
            empty.getStyleClass().add("muted-text");
            contentArea.getChildren().add(empty);
            return;
        }

        VBox table = new VBox();
        table.getStyleClass().add("txn-table");
        table.getChildren().add(buildHedgeHeader());
        for (HedgeDTO hedge : hedges) {
            table.getChildren().add(buildHedgeRow(hedge));
        }
        contentArea.getChildren().add(table);
    }

    private HBox buildHedgesActionBar() {
        Button add = new Button("+ New Hedge");
        add.getStyleClass().add("action-button");
        add.setOnAction(e -> openCreateHedgeDialog());

        HBox bar = new HBox(add);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private HBox buildHedgeHeader() {
        HBox header = new HBox();
        header.getStyleClass().add("txn-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(
                headerCell("INSTRUMENT", 120),
                headerCell("SIDE", 80),
                headerCell("PAIR", 90),
                headerCell("NOTIONAL", 150),
                headerCell("CONTRACT", 100),
                headerCell("UNREALISED P&L", 0),
                headerCell("EFFECTIVE", 110),
                headerCell("STATUS", 100));
        return header;
    }

    private HBox buildHedgeRow(HedgeDTO hedge) {
        HBox row = new HBox();
        row.getStyleClass().add("txn-row");
        row.setAlignment(Pos.CENTER_LEFT);

        String instrumentText = hedge.getInstrumentType()
                + (hedge.getOptionType() != null ? " " + hedge.getOptionType() : "");
        Label instrument = new Label(instrumentText);
        instrument.getStyleClass().addAll("txn-badge", "txn-type-" + hedge.getInstrumentType().toLowerCase());
        HBox instrumentBox = new HBox(instrument);
        instrumentBox.setMinWidth(120);
        instrumentBox.setPrefWidth(120);
        instrumentBox.setAlignment(Pos.CENTER_LEFT);

        Label side = new Label(hedge.getDirection());
        side.getStyleClass().addAll("txn-badge", "txn-type-" + hedge.getDirection().toLowerCase());
        HBox sideBox = new HBox(side);
        sideBox.setMinWidth(80);
        sideBox.setPrefWidth(80);
        sideBox.setAlignment(Pos.CENTER_LEFT);

        Label pair = new Label(hedge.getBaseCurrency() + "/" + hedge.getQuoteCurrency());
        pair.getStyleClass().add("txn-cell");
        pair.setMinWidth(90);
        pair.setPrefWidth(90);

        Label notional = new Label(formatMoney(hedge.getNotional()) + " " + hedge.getBaseCurrency());
        notional.getStyleClass().add("txn-cell");
        notional.setMinWidth(150);
        notional.setPrefWidth(150);

        Label contract = new Label(hedge.getContractRate() != null
                ? hedge.getContractRate().toPlainString() : "—");
        contract.getStyleClass().add("txn-cell-muted");
        contract.setMinWidth(100);
        contract.setPrefWidth(100);

        Label pnl = pnlLabel(hedge.getUnrealizedPnl(), hedge.getQuoteCurrency());
        pnl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pnl, Priority.ALWAYS);

        Label effectiveness = new Label(hedgeEffectivenessText(hedge));
        effectiveness.getStyleClass().add("txn-cell-muted");
        if (Boolean.TRUE.equals(hedge.getEffective())) {
            effectiveness.getStyleClass().add("pnl-positive");
        } else if (hedge.getEffectivenessPercent() != null) {
            effectiveness.getStyleClass().add("pnl-negative");
        }
        effectiveness.setMinWidth(110);
        effectiveness.setPrefWidth(110);

        Label status = new Label(hedge.getStatus());
        status.getStyleClass().addAll("txn-badge", "txn-status-" + hedge.getStatus().toLowerCase());
        HBox statusBox = new HBox(status);
        statusBox.setMinWidth(100);
        statusBox.setPrefWidth(100);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        row.getChildren().addAll(instrumentBox, sideBox, pair, notional, contract, pnl, effectiveness, statusBox);
        return row;
    }

    private String hedgeEffectivenessText(HedgeDTO hedge) {
        if (hedge.getEffectivenessPercent() == null) {
            return "—";
        }
        String mark = Boolean.TRUE.equals(hedge.getEffective()) ? " ✓" : " ✗";
        return hedge.getEffectivenessPercent().toPlainString() + "%" + mark;
    }

    /**
     * Opens a modal form for booking a forward or option. The exposure picker is
     * optional — a hedge can stand alone or be linked to the position it covers, in
     * which case its hedge ratio and effectiveness are derived on the fly.
     */
    private void openCreateHedgeDialog() {
        AuthResponseDTO session = SessionManager.getSession();
        if (session == null) return;
        Long userId = session.getUserId();

        ComboBox<String> instrumentBox = new ComboBox<>();
        instrumentBox.getItems().addAll(Hedge.INSTRUMENT_FORWARD, Hedge.INSTRUMENT_OPTION);
        instrumentBox.setValue(Hedge.INSTRUMENT_FORWARD);
        instrumentBox.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> optionTypeBox = new ComboBox<>();
        optionTypeBox.getItems().addAll(Hedge.OPTION_CALL, Hedge.OPTION_PUT);
        optionTypeBox.setMaxWidth(Double.MAX_VALUE);
        optionTypeBox.setPromptText("CALL / PUT (options only)");

        ComboBox<String> directionBox = new ComboBox<>();
        directionBox.getItems().addAll(Hedge.DIRECTION_BUY, Hedge.DIRECTION_SELL);
        directionBox.setValue(Hedge.DIRECTION_SELL);
        directionBox.setMaxWidth(Double.MAX_VALUE);

        TextField base = dialogField("EUR");
        TextField quote = dialogField("USD");
        TextField notional = dialogField("0.00");
        TextField contractRate = dialogField("0.000000");
        TextField premium = dialogField("optional (options)");
        DatePicker maturity = new DatePicker();
        maturity.setMaxWidth(Double.MAX_VALUE);
        maturity.setPromptText("optional");
        TextField description = dialogField("optional");

        ComboBox<ExposureDTO> exposureBox = new ComboBox<>();
        exposureBox.setMaxWidth(Double.MAX_VALUE);
        exposureBox.setPromptText("None");
        exposureBox.setConverter(new StringConverter<ExposureDTO>() {
            @Override
            public String toString(ExposureDTO exposure) {
                return exposure == null ? "None"
                        : exposure.getType() + " " + formatMoney(exposure.getAmount()) + " " + exposure.getCurrency()
                        + (exposure.getCounterparty() != null ? " — " + exposure.getCounterparty() : "");
            }

            @Override
            public ExposureDTO fromString(String string) {
                return null;
            }
        });
        try {
            exposureBox.getItems().addAll(exposureService.getUserExposures(userId));
        } catch (Exception ignored) {
            // A failure to preload exposures just means the picker stays empty.
        }

        Label error = new Label();
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setManaged(false);
        error.setVisible(false);

        Button submit = new Button("Book Hedge");
        submit.getStyleClass().add("primary-button");
        submit.setMaxWidth(Double.MAX_VALUE);

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("link-text");
        cancel.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label("New Hedge");
        title.getStyleClass().add("dialog-title");

        VBox form = new VBox(12);
        form.getStyleClass().add("dialog-form");
        form.setPrefWidth(360);
        form.getChildren().addAll(
                title,
                labeledControl("INSTRUMENT", instrumentBox),
                labeledControl("OPTION TYPE", optionTypeBox),
                labeledControl("SIDE", directionBox),
                labeledControl("BASE CURRENCY", base),
                labeledControl("QUOTE CURRENCY", quote),
                labeledControl("NOTIONAL (BASE)", notional),
                labeledControl("CONTRACT / STRIKE RATE", contractRate),
                labeledControl("PREMIUM", premium),
                labeledControl("MATURITY DATE", maturity),
                labeledControl("COVERS EXPOSURE", exposureBox),
                labeledControl("DESCRIPTION", description),
                error,
                submit,
                cancel);

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-scroll");

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(contentArea.getScene().getWindow());
        dialog.setTitle("New Hedge");

        cancel.setOnAction(e -> dialog.close());

        submit.setOnAction(e -> {
            error.setManaged(false);
            error.setVisible(false);

            String baseCode = base.getText().trim();
            String quoteCode = quote.getText().trim();
            if (baseCode.isEmpty() || quoteCode.isEmpty()) {
                showDialogError(error, "Base and quote currency are required.");
                return;
            }

            BigDecimal notionalVal;
            BigDecimal rateVal;
            BigDecimal premiumVal = null;
            try {
                notionalVal = new BigDecimal(notional.getText().trim());
                rateVal = new BigDecimal(contractRate.getText().trim());
                String premiumText = premium.getText().trim();
                if (!premiumText.isEmpty() && !premiumText.startsWith("optional")) {
                    premiumVal = new BigDecimal(premiumText);
                }
            } catch (NumberFormatException ex) {
                showDialogError(error, "Notional, rate and premium must be valid numbers.");
                return;
            }
            if (notionalVal.signum() <= 0 || rateVal.signum() <= 0) {
                showDialogError(error, "Notional and rate must be greater than zero.");
                return;
            }
            if (Hedge.INSTRUMENT_OPTION.equals(instrumentBox.getValue()) && optionTypeBox.getValue() == null) {
                showDialogError(error, "Select CALL or PUT for an option.");
                return;
            }

            CreateHedgeRequestDTO request = new CreateHedgeRequestDTO();
            request.setInstrumentType(instrumentBox.getValue());
            request.setOptionType(optionTypeBox.getValue());
            request.setDirection(directionBox.getValue());
            request.setBaseCurrency(baseCode);
            request.setQuoteCurrency(quoteCode);
            request.setNotional(notionalVal);
            request.setContractRate(rateVal);
            request.setPremium(premiumVal);
            request.setExposureId(exposureBox.getValue() != null ? exposureBox.getValue().getId() : null);
            request.setTradeDate(LocalDate.now());
            request.setMaturityDate(maturity.getValue());
            request.setDescription(emptyToNull(description.getText()));

            submit.setDisable(true);
            Thread t = new Thread(() -> {
                try {
                    hedgeService.createHedge(userId, request);
                    Platform.runLater(() -> {
                        dialog.close();
                        showHedges();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        showDialogError(error, "Could not book hedge: " + ex.getMessage());
                        submit.setDisable(false);
                    });
                }
            }, "create-hedge");
            t.setDaemon(true);
            t.start();
        });

        Scene scene = new Scene(scroll, 380, 600);
        scene.getStylesheets().add(
                getClass().getResource("/css/styles.css").toExternalForm());
        dialog.setScene(scene);
        dialog.setResizable(false);
        dialog.showAndWait();
    }

    // ===================== Rate Alerts =====================

    @FXML
    private void showAlerts() {
        pageTitle.setText("Rate Alerts");
        contentArea.getChildren().clear();

        AuthResponseDTO session = SessionManager.getSession();
        if (session == null) return;

        Label loading = new Label("Loading alerts...");
        loading.getStyleClass().add("muted-text");
        contentArea.getChildren().addAll(buildAlertsActionBar(), loading);

        Long userId = session.getUserId();
        Thread t = new Thread(() -> {
            try {
                List<AlertDTO> alerts = rateAlertService.getUserAlerts(userId);
                Platform.runLater(() -> displayAlerts(alerts));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    contentArea.getChildren().clear();
                    contentArea.getChildren().add(buildAlertsActionBar());
                    Label err = new Label("Alerts unavailable.");
                    err.getStyleClass().add("muted-text");
                    contentArea.getChildren().add(err);
                });
            }
        }, "alert-loader");
        t.setDaemon(true);
        t.start();
    }

    private void displayAlerts(List<AlertDTO> alerts) {
        contentArea.getChildren().clear();
        contentArea.getChildren().add(buildAlertsActionBar());

        if (alerts.isEmpty()) {
            Label empty = new Label("No alerts yet. Create a threshold alert on any currency pair.");
            empty.getStyleClass().add("muted-text");
            contentArea.getChildren().add(empty);
            return;
        }

        VBox table = new VBox();
        table.getStyleClass().add("txn-table");
        table.getChildren().add(buildAlertHeader());
        for (AlertDTO alert : alerts) {
            table.getChildren().add(buildAlertRow(alert));
        }
        contentArea.getChildren().add(table);
    }

    private HBox buildAlertsActionBar() {
        Button add = new Button("+ New Alert");
        add.getStyleClass().add("action-button");
        add.setOnAction(e -> openCreateAlertDialog());

        HBox bar = new HBox(add);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private HBox buildAlertHeader() {
        HBox header = new HBox();
        header.getStyleClass().add("txn-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(
                headerCell("PAIR", 120),
                headerCell("CONDITION", 170),
                headerCell("LAST RATE", 130),
                headerCell("STATUS", 120),
                headerCell("", 0));
        return header;
    }

    private HBox buildAlertRow(AlertDTO alert) {
        HBox row = new HBox();
        row.getStyleClass().add("txn-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label pair = new Label(alert.getBase() + "/" + alert.getQuote());
        pair.getStyleClass().add("txn-cell");
        pair.setMinWidth(120);
        pair.setPrefWidth(120);

        String arrow = RateAlert.DIRECTION_ABOVE.equalsIgnoreCase(alert.getDirection()) ? "≥" : "≤";
        Label condition = new Label(arrow + " " + alert.getThreshold().toPlainString());
        condition.getStyleClass().add("txn-cell");
        condition.setMinWidth(170);
        condition.setPrefWidth(170);

        Label lastRate = new Label(alert.getLastCheckedRate() != null
                ? alert.getLastCheckedRate().toPlainString() : "—");
        lastRate.getStyleClass().add("txn-cell-muted");
        lastRate.setMinWidth(130);
        lastRate.setPrefWidth(130);

        Label status = new Label(alert.getStatus());
        status.getStyleClass().addAll("txn-badge", "txn-status-" + alert.getStatus().toLowerCase());
        HBox statusBox = new HBox(status);
        statusBox.setMinWidth(120);
        statusBox.setPrefWidth(120);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(actions, Priority.ALWAYS);
        if (RateAlert.STATUS_TRIGGERED.equalsIgnoreCase(alert.getStatus())) {
            Button rearm = new Button("Re-arm");
            rearm.getStyleClass().add("link-text");
            rearm.setOnAction(e -> runAlertAction(() -> rateAlertService.rearmAlert(currentUserId(), alert.getId())));
            actions.getChildren().add(rearm);
        }
        Button delete = new Button("Delete");
        delete.getStyleClass().add("link-text");
        delete.setOnAction(e -> runAlertAction(() -> rateAlertService.deleteAlert(currentUserId(), alert.getId())));
        actions.getChildren().add(delete);

        row.getChildren().addAll(pair, condition, lastRate, statusBox, actions);
        return row;
    }

    /** Runs a quick alert mutation off the UI thread, then refreshes the list. */
    private void runAlertAction(Runnable action) {
        Thread t = new Thread(() -> {
            try {
                action.run();
            } catch (Exception ignored) {
                // The refresh will still reflect the true state.
            } finally {
                Platform.runLater(this::showAlerts);
            }
        }, "alert-action");
        t.setDaemon(true);
        t.start();
    }

    private void openCreateAlertDialog() {
        AuthResponseDTO session = SessionManager.getSession();
        if (session == null) return;
        Long userId = session.getUserId();

        TextField base = dialogField("USD");
        TextField quote = dialogField("EUR");

        ComboBox<String> directionBox = new ComboBox<>();
        directionBox.getItems().addAll(RateAlert.DIRECTION_ABOVE, RateAlert.DIRECTION_BELOW);
        directionBox.setValue(RateAlert.DIRECTION_ABOVE);
        directionBox.setMaxWidth(Double.MAX_VALUE);

        TextField threshold = dialogField("0.000000");
        TextField note = dialogField("optional");

        Label error = new Label();
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setManaged(false);
        error.setVisible(false);

        Button submit = new Button("Create Alert");
        submit.getStyleClass().add("primary-button");
        submit.setMaxWidth(Double.MAX_VALUE);

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("link-text");
        cancel.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label("New Rate Alert");
        title.getStyleClass().add("dialog-title");

        VBox form = new VBox(12);
        form.getStyleClass().add("dialog-form");
        form.setPrefWidth(360);
        form.getChildren().addAll(
                title,
                labeledControl("BASE CURRENCY", base),
                labeledControl("QUOTE CURRENCY", quote),
                labeledControl("FIRE WHEN RATE IS", directionBox),
                labeledControl("THRESHOLD", threshold),
                labeledControl("NOTE", note),
                error,
                submit,
                cancel);

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(contentArea.getScene().getWindow());
        dialog.setTitle("New Rate Alert");

        cancel.setOnAction(e -> dialog.close());

        submit.setOnAction(e -> {
            error.setManaged(false);
            error.setVisible(false);

            String baseCode = base.getText().trim();
            String quoteCode = quote.getText().trim();
            if (baseCode.isEmpty() || quoteCode.isEmpty()) {
                showDialogError(error, "Base and quote currency are required.");
                return;
            }

            BigDecimal thresholdVal;
            try {
                thresholdVal = new BigDecimal(threshold.getText().trim());
            } catch (NumberFormatException ex) {
                showDialogError(error, "Threshold must be a valid number.");
                return;
            }
            if (thresholdVal.signum() <= 0) {
                showDialogError(error, "Threshold must be greater than zero.");
                return;
            }

            CreateAlertRequestDTO request = new CreateAlertRequestDTO();
            request.setBase(baseCode);
            request.setQuote(quoteCode);
            request.setDirection(directionBox.getValue());
            request.setThreshold(thresholdVal);
            request.setNote(emptyToNull(note.getText()));

            submit.setDisable(true);
            Thread t = new Thread(() -> {
                try {
                    rateAlertService.createAlert(userId, request);
                    Platform.runLater(() -> {
                        dialog.close();
                        showAlerts();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        showDialogError(error, "Could not create alert: " + ex.getMessage());
                        submit.setDisable(false);
                    });
                }
            }, "create-alert");
            t.setDaemon(true);
            t.start();
        });

        Scene scene = new Scene(form);
        scene.getStylesheets().add(
                getClass().getResource("/css/styles.css").toExternalForm());
        dialog.setScene(scene);
        dialog.setResizable(false);
        dialog.showAndWait();
    }

    // ===================== Analytics =====================

    @FXML
    private void showAnalytics() {
        pageTitle.setText("Analytics");
        contentArea.getChildren().clear();

        AuthResponseDTO session = SessionManager.getSession();
        if (session == null) return;

        Label loading = new Label("Running stress tests, VaR and P&L attribution...");
        loading.getStyleClass().add("muted-text");
        contentArea.getChildren().add(loading);

        Long userId = session.getUserId();
        Thread t = new Thread(() -> {
            try {
                StressTestResultDTO stress = scenarioAnalysisService.runStressTests(userId, HOME_CURRENCY);
                VarResultDTO var = riskMetricsService.valueAtRisk(userId, HOME_CURRENCY, null, 365);
                AttributionResultDTO attribution = riskMetricsService.pnlAttribution(userId, HOME_CURRENCY, 30);
                Platform.runLater(() -> displayAnalytics(stress, var, attribution));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    contentArea.getChildren().clear();
                    Label err = new Label("Analytics unavailable — exchange rates could not be loaded.");
                    err.getStyleClass().add("muted-text");
                    contentArea.getChildren().add(err);
                });
            }
        }, "analytics-loader");
        t.setDaemon(true);
        t.start();
    }

    private void displayAnalytics(StressTestResultDTO stress, VarResultDTO var, AttributionResultDTO attribution) {
        contentArea.getChildren().clear();

        contentArea.getChildren().add(buildVarCard(var));

        contentArea.getChildren().add(sectionCaption("STRESS TESTS"));
        if (stress.getScenarios().isEmpty()) {
            contentArea.getChildren().add(mutedNote("No positions to stress-test yet."));
        } else {
            VBox table = new VBox();
            table.getStyleClass().add("txn-table");
            table.getChildren().add(buildStressHeader());
            for (ScenarioResultDTO scenario : stress.getScenarios()) {
                table.getChildren().add(buildStressRow(scenario));
            }
            contentArea.getChildren().add(table);
        }

        contentArea.getChildren().add(sectionCaption("FX P&L ATTRIBUTION (LAST 30 DAYS)"));
        if (attribution.getBreakdown().isEmpty()) {
            contentArea.getChildren().add(mutedNote(
                    "No stored rate history yet — attribution needs at least two rate snapshots per pair."));
        } else {
            VBox table = new VBox();
            table.getStyleClass().add("txn-table");
            table.getChildren().add(buildAttributionHeader());
            for (CurrencyAttributionDTO entry : attribution.getBreakdown()) {
                table.getChildren().add(buildAttributionRow(entry, attribution.getHome()));
            }
            contentArea.getChildren().add(table);
        }
    }

    private VBox buildVarCard(VarResultDTO var) {
        VBox card = new VBox(4);
        card.getStyleClass().add("stat-summary-card");
        card.setPadding(new Insets(20));

        Label caption = new Label("VALUE AT RISK (95%, HISTORICAL SIMULATION)");
        caption.getStyleClass().add("stat-summary-caption");

        if (var.getObservations() == 0) {
            Label value = new Label("Not enough history");
            value.getStyleClass().add("stat-summary-value");
            Label sub = new Label(var.getMessage() != null ? var.getMessage() : "");
            sub.getStyleClass().add("stat-summary-sub");
            sub.setWrapText(true);
            card.getChildren().addAll(caption, value, sub);
            return card;
        }

        Label value = new Label(formatMoney(var.getValueAtRisk()) + " " + var.getHome());
        value.getStyleClass().add("stat-summary-value");

        Label sub = new Label("Expected shortfall " + formatMoney(var.getExpectedShortfall()) + " " + var.getHome()
                + "  ·  worst " + formatMoney(var.getWorstLoss()) + " " + var.getHome()
                + "  ·  " + var.getObservations() + " observations");
        sub.getStyleClass().add("stat-summary-sub");

        card.getChildren().addAll(caption, value, sub);
        return card;
    }

    private HBox buildStressHeader() {
        HBox header = new HBox();
        header.getStyleClass().add("txn-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(
                headerCell("SCENARIO", 0),
                headerCell("SHOCKED VALUE", 180),
                headerCell("P&L", 160));
        return header;
    }

    private HBox buildStressRow(ScenarioResultDTO scenario) {
        HBox row = new HBox();
        row.getStyleClass().add("txn-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label name = new Label(scenario.getName());
        name.getStyleClass().add("txn-cell");
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);

        Label shocked = new Label(formatMoney(scenario.getShockedValue()) + " " + scenario.getHome());
        shocked.getStyleClass().add("txn-cell");
        shocked.setMinWidth(180);
        shocked.setPrefWidth(180);

        Label pnl = pnlLabel(scenario.getPnl(), scenario.getHome());
        pnl.setMinWidth(160);
        pnl.setPrefWidth(160);

        row.getChildren().addAll(name, shocked, pnl);
        return row;
    }

    private HBox buildAttributionHeader() {
        HBox header = new HBox();
        header.getStyleClass().add("txn-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(
                headerCell("CURRENCY", 120),
                headerCell("RATE CHANGE", 150),
                headerCell("P&L", 0));
        return header;
    }

    private HBox buildAttributionRow(CurrencyAttributionDTO entry, String home) {
        HBox row = new HBox();
        row.getStyleClass().add("txn-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label currency = new Label(entry.getCurrency());
        currency.getStyleClass().add("txn-cell");
        currency.setMinWidth(120);
        currency.setPrefWidth(120);

        Label change = new Label(entry.getRateChangePercent() != null
                ? entry.getRateChangePercent().toPlainString() + "%" : "—");
        change.getStyleClass().add("txn-cell-muted");
        change.setMinWidth(150);
        change.setPrefWidth(150);

        Label pnl = pnlLabel(entry.getPnl(), home);
        pnl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pnl, Priority.ALWAYS);

        row.getChildren().addAll(currency, change, pnl);
        return row;
    }

    // ===================== Shared helpers =====================

    private Long currentUserId() {
        AuthResponseDTO session = SessionManager.getSession();
        return session != null ? session.getUserId() : null;
    }

    private Label sectionCaption(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("stat-summary-caption");
        return label;
    }

    private Label mutedNote(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted-text");
        label.setWrapText(true);
        return label;
    }

    /** A money label coloured green for a gain and red for a loss. */
    private Label pnlLabel(BigDecimal value, String suffix) {
        Label label = new Label(value != null
                ? formatMoney(value) + (suffix != null ? " " + suffix : "") : "—");
        label.getStyleClass().add("txn-cell");
        if (value != null) {
            label.getStyleClass().add(value.signum() < 0 ? "pnl-negative" : "pnl-positive");
        }
        return label;
    }

    @FXML
    private void showTransactions() {
        pageTitle.setText("Transactions");
        contentArea.getChildren().clear();

        AuthResponseDTO session = SessionManager.getSession();
        if (session == null) return;

        Label loading = new Label("Loading transactions...");
        loading.getStyleClass().add("muted-text");
        contentArea.getChildren().addAll(buildTransactionsActionBar(), loading);

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
        contentArea.getChildren().add(buildTransactionsActionBar());

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

    private HBox buildTransactionsActionBar() {
        Button add = new Button("+ New Transaction");
        add.getStyleClass().add("action-button");
        add.setOnAction(e -> openCreateTransactionDialog());

        HBox bar = new HBox(add);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    /**
     * Opens a modal form for creating a transaction. On success the dialog
     * closes and the transaction list refreshes. Wallet pickers are optional,
     * so single-sided transactions (deposit/withdrawal) and two-sided ones
     * (exchange/transfer) are both expressible. Ownership of any selected
     * wallet is enforced server-side by {@link TransactionService}.
     */
    private void openCreateTransactionDialog() {
        AuthResponseDTO session = SessionManager.getSession();
        if (session == null) return;
        Long userId = session.getUserId();

        List<Wallet> wallets = walletRepository.findByUserId(userId);

        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll("EXCHANGE", "DEPOSIT", "WITHDRAWAL", "TRANSFER");
        typeBox.setValue("EXCHANGE");
        typeBox.setMaxWidth(Double.MAX_VALUE);

        ComboBox<Wallet> fromBox = walletComboBox(wallets);
        ComboBox<Wallet> toBox = walletComboBox(wallets);

        TextField fromAmount = dialogField("0.00");
        TextField toAmount = dialogField("0.00");
        TextField rate = dialogField("optional");
        TextField fee = dialogField("0.00");
        TextField description = dialogField("optional");

        Label error = new Label();
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setManaged(false);
        error.setVisible(false);

        Button submit = new Button("Create Transaction");
        submit.getStyleClass().add("primary-button");
        submit.setMaxWidth(Double.MAX_VALUE);

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("link-text");
        cancel.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label("New Transaction");
        title.getStyleClass().add("dialog-title");

        VBox form = new VBox(12);
        form.getStyleClass().add("dialog-form");
        form.setPrefWidth(360);
        form.getChildren().addAll(
                title,
                labeledControl("TYPE", typeBox),
                labeledControl("FROM WALLET", fromBox),
                labeledControl("TO WALLET", toBox),
                labeledControl("FROM AMOUNT", fromAmount),
                labeledControl("TO AMOUNT", toAmount),
                labeledControl("EXCHANGE RATE", rate),
                labeledControl("FEE", fee),
                labeledControl("DESCRIPTION", description),
                error,
                submit,
                cancel);

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(contentArea.getScene().getWindow());
        dialog.setTitle("New Transaction");

        cancel.setOnAction(e -> dialog.close());

        submit.setOnAction(e -> {
            error.setManaged(false);
            error.setVisible(false);

            BigDecimal fromAmt;
            BigDecimal toAmt;
            BigDecimal rateVal = null;
            BigDecimal feeVal = null;
            try {
                fromAmt = new BigDecimal(fromAmount.getText().trim());
                toAmt = new BigDecimal(toAmount.getText().trim());
                if (!rate.getText().trim().isEmpty()) rateVal = new BigDecimal(rate.getText().trim());
                if (!fee.getText().trim().isEmpty()) feeVal = new BigDecimal(fee.getText().trim());
            } catch (NumberFormatException ex) {
                showDialogError(error, "Amounts, rate and fee must be valid numbers.");
                return;
            }

            if (fromAmt.signum() <= 0 || toAmt.signum() <= 0) {
                showDialogError(error, "From and To amounts must be greater than zero.");
                return;
            }

            CreateTransactionRequestDTO request = new CreateTransactionRequestDTO();
            request.setTransactionType(typeBox.getValue());
            request.setFromWalletId(fromBox.getValue() != null ? fromBox.getValue().getId() : null);
            request.setToWalletId(toBox.getValue() != null ? toBox.getValue().getId() : null);
            request.setFromAmount(fromAmt);
            request.setToAmount(toAmt);
            request.setExchangeRateUsed(rateVal);
            request.setFeeAmount(feeVal);
            String desc = description.getText().trim();
            request.setDescription(desc.isEmpty() ? null : desc);

            submit.setDisable(true);
            Thread t = new Thread(() -> {
                try {
                    transactionService.createTransaction(userId, request);
                    Platform.runLater(() -> {
                        dialog.close();
                        showTransactions();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        showDialogError(error, "Could not create transaction: " + ex.getMessage());
                        submit.setDisable(false);
                    });
                }
            }, "create-transaction");
            t.setDaemon(true);
            t.start();
        });

        Scene scene = new Scene(form);
        scene.getStylesheets().add(
                getClass().getResource("/css/styles.css").toExternalForm());
        dialog.setScene(scene);
        dialog.setResizable(false);
        dialog.showAndWait();
    }

    private ComboBox<Wallet> walletComboBox(List<Wallet> wallets) {
        ComboBox<Wallet> box = new ComboBox<>();
        box.getItems().addAll(wallets);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setPromptText("None");
        box.setConverter(new StringConverter<Wallet>() {
            @Override
            public String toString(Wallet wallet) {
                return wallet == null ? "None"
                        : wallet.getCurrency() + " — " + wallet.getBalance().toPlainString();
            }

            @Override
            public Wallet fromString(String string) {
                return null;
            }
        });
        return box;
    }

    private TextField dialogField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("input-field");
        return field;
    }

    private VBox labeledControl(String labelText, Node control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        return new VBox(4, label, control);
    }

    private void showDialogError(Label error, String message) {
        error.setText(message);
        error.setManaged(true);
        error.setVisible(true);
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

    private VBox buildWalletCard(Wallet wallet) {
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
