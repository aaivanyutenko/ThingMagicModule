package co.bytetechnology.thm;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import co.bytetechnology.thm.databinding.ActivityMainBinding;
import me.pantre.app.bean.peripheral.DragonFruitFacade;
import me.pantre.app.peripheral.model.TagReadData;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Timber.plant(new Timber.DebugTree());
        DragonFruitFacade dragonFruitFacade = new DragonFruitFacade(this);
        dragonFruitFacade.initPeripherals();
    }

    public void onTagReads(TagReadData[] tagReads) {
    }
}
