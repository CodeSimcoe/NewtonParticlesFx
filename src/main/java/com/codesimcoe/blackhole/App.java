package com.codesimcoe.blackhole;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

public final class App extends Application {

  private final Label mode = label("VALHALLA VALUE OBJECTS", 13, "#ffcf83");
  private final Label progress = label("PREPARING CINEMATIC RENDER", 13, "#bfc8dc");
  private final Label metrics = label("", 13, "#bfc8dc");
  private Button cinematicButton;
  private Button valueBenchmarkButton;
  private Button referenceBenchmarkButton;

  @Override
  public void start(Stage stage) {
    ImageView view = new ImageView();
    view.setPreserveRatio(true);
    view.setSmooth(true);
    view.setFitWidth(Constants.DEFAULT_WIDTH);
    view.setFitHeight(Constants.DEFAULT_HEIGHT);

    StackPane root = new StackPane(view);
    root.setStyle("-fx-background-color: #02030a;");

    Label title = label("EVENT HORIZON", 34, "#fff0d2");
    title.setFont(Font.font("System", FontWeight.LIGHT, 34));
    Label subtitle = label("CINEMATIC SCHWARZSCHILD RAY TRACER", 12, "#9aa6c0");

    cinematicButton = button("CINEMATIC VALUE RENDER");
    valueBenchmarkButton = button("VALUE BENCH 640 x 360");
    referenceBenchmarkButton = button("REFERENCE BENCH 640 x 360");
    HBox controls = new HBox(8, cinematicButton, valueBenchmarkButton, referenceBenchmarkButton);

    VBox panel = new VBox(7, title, subtitle, mode, progress, metrics, controls);
    panel.setPadding(new Insets(24));
    panel.setMaxWidth(440);
    panel.setStyle("-fx-background-color: rgba(3, 5, 15, 0.72); -fx-border-color: rgba(255, 207, 131, 0.30); -fx-border-width: 0 0 1 0;");
    StackPane.setAlignment(panel, Pos.TOP_LEFT);
    root.getChildren().add(panel);

    cinematicButton.setOnAction(_ -> startValueRender(
      view,
      Constants.DEFAULT_WIDTH,
      Constants.DEFAULT_HEIGHT,
      "CINEMATIC VALUE RENDER"
    ));
    valueBenchmarkButton.setOnAction(_ -> startValueRender(
      view,
      Constants.COMPARISON_WIDTH,
      Constants.COMPARISON_HEIGHT,
      "VALUE ARRAY BENCHMARK"
    ));
    referenceBenchmarkButton.setOnAction(_ -> startReferenceRender(view));

    stage.setTitle("Event Horizon - Valhalla Value Types");
    stage.setScene(new Scene(root, Constants.DEFAULT_WIDTH, Constants.DEFAULT_HEIGHT, Color.BLACK));
    stage.show();

    startValueRender(
      view,
      Constants.DEFAULT_WIDTH,
      Constants.DEFAULT_HEIGHT,
      "CINEMATIC VALUE RENDER"
    );
  }

  private void startValueRender(ImageView view, int width, int height, String renderMode) {
    beginRender(renderMode, width, height);
    Renderer renderer = new Renderer(width, height, listener(renderMode));
    view.setImage(renderer.image());
    startThread(renderer::render, renderMode);
  }

  private void startReferenceRender(ImageView view) {
    String renderMode = "REFERENCE OBJECT BENCHMARK";
    beginRender(renderMode, Constants.COMPARISON_WIDTH, Constants.COMPARISON_HEIGHT);
    ReferenceRenderer renderer = new ReferenceRenderer(
      Constants.COMPARISON_WIDTH,
      Constants.COMPARISON_HEIGHT,
      listener(renderMode)
    );
    view.setImage(renderer.image());
    startThread(renderer::render, renderMode);
  }

  private void beginRender(String renderMode, int width, int height) {
    setControlsDisabled(true);
    mode.setText(renderMode);
    progress.setText("TRACING " + width + " x " + height + " PHOTON PATHS");
    metrics.setText("same scene | same integrator | same post-process");
  }

  private RenderListener listener(String renderMode) {
    return new RenderListener() {
      @Override
      public void onProgress(int completedBatches, int totalBatches) {
        Platform.runLater(() -> progress.setText(
          "TRACING PHOTON PATHS  " + completedBatches + " / " + totalBatches
        ));
      }

      @Override
      public void onComplete(RenderStats stats) {
        Platform.runLater(() -> {
          mode.setText(renderMode + " COMPLETE");
          progress.setText(String.format("%.2f s | %,d traced steps", stats.elapsedSeconds(), stats.tracedSteps()));
          metrics.setText(String.format("%,.1f MiB allocated on render workers", stats.allocatedMegabytes()));
          setControlsDisabled(false);
        });
      }
    };
  }

  private void startThread(Runnable action, String renderMode) {
    Thread thread = new Thread(() -> {
      try {
        action.run();
      } catch (RuntimeException error) {
        Platform.runLater(() -> {
          mode.setText(renderMode + " FAILED");
          progress.setText(error.getClass().getSimpleName() + ": " + error.getMessage());
          setControlsDisabled(false);
        });
      }
    }, "black-hole-" + renderMode.toLowerCase().replace(' ', '-'));
    thread.setDaemon(true);
    thread.start();
  }

  private void setControlsDisabled(boolean disabled) {
    cinematicButton.setDisable(disabled);
    valueBenchmarkButton.setDisable(disabled);
    referenceBenchmarkButton.setDisable(disabled);
  }

  private static Label label(String text, int size, String color) {
    Label label = new Label(text);
    label.setFont(Font.font("Monospaced", size));
    label.setTextFill(Color.web(color));

    return label;
  }

  private static Button button(String text) {
    Button button = new Button(text);
    button.setStyle("-fx-background-color: #17213a; -fx-text-fill: #e7edf8; -fx-font-family: Monospaced; -fx-font-size: 11px;");

    return button;
  }

  public static void main(String[] args) {
    launch(args);
  }
}
