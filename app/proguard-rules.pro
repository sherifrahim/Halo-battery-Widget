# BluetoothDevice hidden methods are looked up by name via reflection on the framework class,
# so nothing in the app itself needs keeping for that. Keep the Glance receiver/callback classes.
-keep class com.halo.btbattery.widget.** { *; }
