
package com.floating.translator;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Button btnStart = findViewById(R.id.btnStart);
        
        btnStart.setOnClickListener(v -> {
            // Check overlay permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                Toast.makeText(this, "Izinkan overlay dulu!", Toast.LENGTH_LONG).show();
                return;
            }
            
            // Start floating service
            Intent serviceIntent = new Intent(this, FloatingTranslatorService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            
            Toast.makeText(this, "Floating Translator dimulai!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
