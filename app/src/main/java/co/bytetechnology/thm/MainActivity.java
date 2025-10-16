package co.bytetechnology.thm;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import co.bytetechnology.thm.databinding.ActivityMainBinding;
import me.pantre.app.bean.peripheral.DragonFruitFacade;
import me.pantre.app.peripheral.model.TagReadData;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        DragonFruitFacade dragonFruitFacade = new DragonFruitFacade(this);
        dragonFruitFacade.initPeripherals();
    }

    public void onTagReads(TagReadData[] tagReads) {
        for (TagReadData tagData : tagReads) {
            System.out.printf("TagReadData: epc = %s, rssi = %s", tagData.getEpc(), tagData.getRssi());
            System.out.println();
        }
    }
}
