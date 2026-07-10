module newtonparticlesfx {
	requires javafx.graphics;
  requires javafx.controls;
  requires java.desktop;
  requires jdk.management;

  exports com.codesimcoe.newtonparticlesfx to javafx.graphics;
  exports com.codesimcoe.blackhole to javafx.graphics;
}
