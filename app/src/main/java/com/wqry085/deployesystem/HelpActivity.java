package com.wqry085.deployesystem;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.wqry085.deployesystem.next.LogView;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class HelpActivity extends AppCompatActivity {

    private LogView logViewer;
    private Socket socket;
    private PrintWriter out;
    private Thread logThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        logViewer = findViewById(R.id.zygotelog);

        startLogging();
    }

    private void startLogging() {
        logThread = new Thread(() -> {
            try {
                socket = new Socket("localhost", 13568);
                out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // First, dump the existing logs
                out.println("DUMP_LOGS");

                String line;
                while (!Thread.currentThread().isInterrupted() && (line = in.readLine()) != null) {
                    final String logLine = line;
                    runOnUiThread(() -> logViewer.appendLog(logLine));
                }
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    final String errorMessage = "Error connecting to log server: " + e.getMessage();
                    runOnUiThread(() -> logViewer.appendLog(errorMessage));
                }
            } finally {
                cleanup();
            }
        });
        logThread.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (logThread != null) {
            logThread.interrupt();
        }
    }

    private void cleanup() {
        try {
            if (out != null) {
                out.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Log or ignore
        }
    }
}
