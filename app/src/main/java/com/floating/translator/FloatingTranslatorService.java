
package com.floating.translator;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class FloatingTranslatorService extends Service {
    
    private WindowManager windowManager;
    private View floatingView;
    private EditText etInput;
    private TextView tvOutput;
    private Button btnTranslate;
    private Button btnSwap;
    private Button btnClose;
    
    private boolean isEnglishToIndonesian = true;
    
    // Drag variables
    private float initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isDragging = false;
    
    @Override
    public IBinder onBind(Intent intent) { return null; }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotification();
        setupFloatingWindow();
        return START_STICKY;
    }
    
    private void createNotification() {
        String channelId = "translator_channel";
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                channelId, "Translator", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, channelId);
        } else {
            builder = new Notification.Builder(this);
        }
        
        Notification notification = builder
            .setContentTitle("Floating Translator")
            .setContentText("Aktif - Terjemahkan teks")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .build();
        
        startForeground(1, notification);
    }
    
    private void setupFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        floatingView = inflater.inflate(R.layout.floating_translator, null);
        
        etInput = floatingView.findViewById(R.id.etInput);
        tvOutput = floatingView.findViewById(R.id.tvOutput);
        btnTranslate = floatingView.findViewById(R.id.btnTranslate);
        btnSwap = floatingView.findViewById(R.id.btnSwap);
        btnClose = floatingView.findViewById(R.id.btnClose);
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            350,
            200,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 50;
        params.y = 150;
        
        windowManager.addView(floatingView, params);
        
        setupButtons(params);
        setupDrag(params);
    }
    
    private void setupButtons(WindowManager.LayoutParams params) {
        btnTranslate.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (!text.isEmpty()) {
                translateText(text);
            }
        });
        
        btnSwap.setOnClickListener(v -> {
            isEnglishToIndonesian = !isEnglishToIndonesian;
            String hint = isEnglishToIndonesian ? "English → Indonesia" : "Indonesia → English";
            Toast.makeText(this, hint, Toast.LENGTH_SHORT).show();
        });
        
        btnClose.setOnClickListener(v -> {
            stopSelf();
        });
    }
    
    private void setupDrag(WindowManager.LayoutParams params) {
        floatingView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    isDragging = true;
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    if (isDragging) {
                        params.x = (int) (initialX + (event.getRawX() - initialTouchX));
                        params.y = (int) (initialY + (event.getRawY() - initialTouchY));
                        windowManager.updateViewLayout(floatingView, params);
                    }
                    return true;
                    
                case MotionEvent.ACTION_UP:
                    isDragging = false;
                    return true;
            }
            return false;
        });
    }
    
    private void translateText(String text) {
        new Thread(() -> {
            try {
                String source = isEnglishToIndonesian ? "en" : "id";
                String target = isEnglishToIndonesian ? "id" : "en";
                
                String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=" 
                    + source + "&tl=" + target + "&dt=t&q=" + URLEncoder.encode(text, "UTF-8");
                
                URL requestUrl = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) requestUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                
                // Parse response
                String response = sb.toString();
                String translated = parseTranslation(response);
                
                runOnUiThread(() -> {
                    tvOutput.setText(translated);
                    
                    // Copy ke clipboard
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("translation", translated);
                    clipboard.setPrimaryClip(clip);
                });
                
            } catch (Exception e) {
                runOnUiThread(() -> tvOutput.setText("Error: " + e.getMessage()));
            }
        }).start();
    }
    
    private String parseTranslation(String response) {
        try {
            // Format: [[["translated","source",...]],...]
            StringBuilder result = new StringBuilder();
            boolean inQuote = false;
            StringBuilder current = new StringBuilder();
            int quoteCount = 0;
            
            for (char c : response.toCharArray()) {
                if (c == '"') {
                    quoteCount++;
                    if (quoteCount == 1) {
                        inQuote = true;
                        continue;
                    } else if (quoteCount == 2) {
                        inQuote = false;
                        result.append(current);
                        break;
                    }
                }
                if (inQuote) {
                    current.append(c);
                }
            }
            
            return result.toString();
        } catch (Exception e) {
            return "Gagal terjemahkan";
        }
    }
    
    @Override
    public void onDestroy() {
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {}
        }
        super.onDestroy();
    }
}
