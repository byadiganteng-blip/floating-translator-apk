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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FloatingTranslatorService extends Service {
    
    private WindowManager windowManager;
    private View floatingView;
    private EditText etInput;
    private TextView tvOutput;
    private Button btnTranslate;
    private Button btnSwap;
    private Button btnClose;
    private boolean isEnglishToIndonesian = true;
    private ExecutorService executor;
    private Handler mainHandler;
    private float initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isDragging = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
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
            .setContentText("Aktif - Created By Yad")
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
        
        // FIX: Set focusable agar keyboard muncul
        etInput.setFocusable(true);
        etInput.setFocusableInTouchMode(true);
        etInput.requestFocus();
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            350, 300,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 50;
        params.y = 100;
        
        windowManager.addView(floatingView, params);
        setupButtons();
        setupDrag(params);
    }
    
    private void setupButtons() {
        btnTranslate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = etInput.getText().toString().trim();
                if (!text.isEmpty()) {
                    translateText(text);
                } else {
                    Toast.makeText(FloatingTranslatorService.this, 
                        "Ketik teks dulu!", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        btnSwap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isEnglishToIndonesian = !isEnglishToIndonesian;
                String hint = isEnglishToIndonesian ? "🇬🇧 EN → 🇮🇩 ID" : "🇮🇩 ID → 🇬🇧 EN";
                Toast.makeText(FloatingTranslatorService.this, hint, Toast.LENGTH_SHORT).show();
            }
        });
        
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopSelf();
            }
        });
    }
    
    private void setupDrag(final WindowManager.LayoutParams params) {
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
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
                    default:
                        return false;
                }
            }
        });
    }
    
    private void translateText(final String text) {
        tvOutput.setText("🔄 Menerjemahkan...");
        
        executor.execute(new Runnable() {
            @Override
            public void run() {
                String translated = translateOnline(text);
                
                if (translated == null || translated.startsWith("Error")) {
                    // Fallback ke offline dictionary
                    translated = translateOffline(text);
                }
                
                final String result = translated;
                
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        tvOutput.setText(result);
                        copyToClipboard(result);
                    }
                });
            }
        });
    }
    
    private String translateOnline(String text) {
        try {
            String source = isEnglishToIndonesian ? "en" : "id";
            String target = isEnglishToIndonesian ? "id" : "en";
            
            String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=" 
                + source + "&tl=" + target + "&dt=t&q=" + URLEncoder.encode(text, "UTF-8");
            
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            
            int code = conn.getResponseCode();
            if (code != 200) return null;
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            conn.disconnect();
            
            return parseTranslation(sb.toString());
        } catch (Exception e) {
            return null;
        }
    }
    
    private String translateOffline(String text) {
        // Simple offline dictionary (EN → ID)
        String lower = text.toLowerCase().trim();
        
        if (isEnglishToIndonesian) {
            String[][] dict = {
                {"hello", "halo"}, {"good morning", "selamat pagi"},
                {"good night", "selamat malam"}, {"thank you", "terima kasih"},
                {"how are you", "apa kabar"}, {"goodbye", "selamat tinggal"},
                {"yes", "ya"}, {"no", "tidak"}, {"please", "tolong"},
                {"sorry", "maaf"}, {"welcome", "selamat datang"},
                {"friend", "teman"}, {"love", "cinta"}, {"happy", "senang"},
                {"sad", "sedih"}, {"angry", "marah"}, {"beautiful", "cantik"},
                {"good", "bagus"}, {"bad", "buruk"}, {"big", "besar"},
                {"small", "kecil"}, {"fast", "cepat"}, {"slow", "lambat"},
                {"hot", "panas"}, {"cold", "dingin"}, {"water", "air"},
                {"food", "makanan"}, {"drink", "minuman"}, {"house", "rumah"},
                {"school", "sekolah"}, {"work", "kerja"}, {"play", "bermain"},
                {"read", "membaca"}, {"write", "menulis"}, {"speak", "berbicara"},
                {"listen", "mendengar"}, {"see", "melihat"}, {"go", "pergi"},
                {"come", "datang"}, {"eat", "makan"}, {"sleep", "tidur"},
                {"i love you", "aku cinta kamu"}, {"what", "apa"},
                {"who", "siapa"}, {"where", "di mana"}, {"when", "kapan"},
                {"why", "mengapa"}, {"how", "bagaimana"},
            };
            
            for (String[] pair : dict) {
                if (lower.equals(pair[0])) return pair[1];
                if (lower.contains(pair[0])) {
                    return text.replaceAll("(?i)" + pair[0], pair[1]);
                }
            }
            return "Terjemahan offline tidak ditemukan untuk: " + text;
        } else {
            String[][] dict = {
                {"halo", "hello"}, {"selamat pagi", "good morning"},
                {"selamat malam", "good night"}, {"terima kasih", "thank you"},
                {"apa kabar", "how are you"}, {"selamat tinggal", "goodbye"},
                {"ya", "yes"}, {"tidak", "no"}, {"tolong", "please"},
                {"maaf", "sorry"}, {"selamat datang", "welcome"},
                {"teman", "friend"}, {"cinta", "love"}, {"senang", "happy"},
                {"sedih", "sad"}, {"marah", "angry"}, {"cantik", "beautiful"},
                {"bagus", "good"}, {"buruk", "bad"}, {"besar", "big"},
                {"kecil", "small"}, {"cepat", "fast"}, {"lambat", "slow"},
                {"panas", "hot"}, {"dingin", "cold"}, {"air", "water"},
                {"makanan", "food"}, {"minuman", "drink"}, {"rumah", "house"},
                {"sekolah", "school"}, {"kerja", "work"}, {"bermain", "play"},
                {"membaca", "read"}, {"menulis", "write"}, {"berbicara", "speak"},
                {"mendengar", "listen"}, {"melihat", "see"}, {"pergi", "go"},
                {"datang", "come"}, {"makan", "eat"}, {"tidur", "sleep"},
                {"aku cinta kamu", "i love you"}, {"apa", "what"},
                {"siapa", "who"}, {"di mana", "where"}, {"kapan", "when"},
                {"mengapa", "why"}, {"bagaimana", "how"},
            };
            
            for (String[] pair : dict) {
                if (lower.equals(pair[0])) return pair[1];
                if (lower.contains(pair[0])) {
                    return text.replaceAll("(?i)" + pair[0], pair[1]);
                }
            }
            return "Offline translation not found for: " + text;
        }
    }
    
    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("translation", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "✅ Tersalin!", Toast.LENGTH_SHORT).show();
        }
    }
    
    private String parseTranslation(String response) {
        try {
            StringBuilder result = new StringBuilder();
            int quoteCount = 0;
            boolean inQuote = false;
            
            for (char c : response.toCharArray()) {
                if (c == '"') {
                    quoteCount++;
                    if (quoteCount == 1) {
                        inQuote = true;
                        continue;
                    }
                    if (quoteCount == 2) break;
                }
                if (inQuote) result.append(c);
            }
            return result.toString();
        } catch (Exception e) {
            return "Gagal terjemahkan";
        }
    }
    
    @Override
    public void onDestroy() {
        if (executor != null) executor.shutdown();
        if (floatingView != null && windowManager != null) {
            try { windowManager.removeView(floatingView); } catch (Exception e) {}
        }
        super.onDestroy();
    }
}