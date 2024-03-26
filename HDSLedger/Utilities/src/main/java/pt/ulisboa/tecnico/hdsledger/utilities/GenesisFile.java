package pt.ulisboa.tecnico.hdsledger.utilities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.json.JSONObject;
import org.json.JSONArray;

public class GenesisFile {

    static CustomLogger LOGGER = new CustomLogger(GenesisFile.class.getName());

    public static boolean read(String path) {
        File file = new File(path);
        return file.exists();
    }

    public static void write(String path, Map<Integer, Integer> balances) throws IOException {
        JSONArray jsonArray = new JSONArray();

        for (Map.Entry<Integer, Integer> entry: balances.entrySet()) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("id", entry.getKey());
            jsonObject.put("balance", entry.getValue());
            jsonArray.put(jsonObject);
        }

        // Write JSON string to file
        try (FileWriter fileWriter = new FileWriter(path)) {
            fileWriter.write(jsonArray.toString());
            System.out.println("Genesis file generated successfully at: " + path);
            LOGGER.log(Level.INFO, MessageFormat.format("Genesis file successfully generated at: {0}", path));
        }
    }
}
