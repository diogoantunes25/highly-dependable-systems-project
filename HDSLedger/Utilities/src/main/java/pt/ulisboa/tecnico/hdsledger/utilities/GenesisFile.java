package pt.ulisboa.tecnico.hdsledger.utilities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.json.JSONObject;
import org.json.JSONArray;

public class GenesisFile {

    static CustomLogger LOGGER = new CustomLogger(GenesisFile.class.getName());

    public static boolean read(String path) {
        File file = new File(path);
        return file.exists();
    }

    public static void write(String path, int n, int c) throws IOException {
        JSONArray jsonArray = new JSONArray();

        // Generate entries for n replicas with initial balance 10
        for (int i = 0; i < n; i++) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("balance", 10);
            jsonObject.put("id", i);
            jsonArray.put(jsonObject);
        }

        // Generate entries for c clients with initial balance 0
        for (int i = n; i < n + c; i++) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("id", i);
            jsonObject.put("balance", 0);
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
