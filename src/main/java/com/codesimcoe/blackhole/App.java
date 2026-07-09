package com.codesimcoe.blackhole;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public final class App extends Application {

  @Override
  public void start(Stage stage) {

    Renderer renderer = new Renderer(
      Constants.DEFAULT_WIDTH,
      Constants.DEFAULT_HEIGHT
    );

    ImageView view = new ImageView(renderer.image());
    view.setPreserveRatio(true);
    view.setSmooth(true);
    view.setFitWidth(Constants.DEFAULT_WIDTH);
    view.setFitHeight(Constants.DEFAULT_HEIGHT);

    StackPane root = new StackPane(view);

    stage.setTitle("Schwarzschild Black Hole - JavaFX");
    stage.setScene(new Scene(root));
    stage.show();

    Thread renderThread = new Thread(renderer::render);
    renderThread.setDaemon(true);
    renderThread.start();
  }

  public static void main(String[] args) {
    launch(args);
  }
}