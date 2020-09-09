package io.sealights.gradle.tests;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class JavaOnlyExampleActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        System.out.println("on create");
    }
}
