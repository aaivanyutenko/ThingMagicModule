package co.bytetechnology.thm;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import co.bytetechnology.thm.databinding.ActivityMainBinding;
import me.pantre.app.bean.peripheral.DragonFruitFacade;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        DragonFruitFacade dragonFruitFacade = new DragonFruitFacade(this);
        dragonFruitFacade.initPeripherals();
    }
}
