////////////////////////////////////////////////////////////////////////////////
//
//  Specie - An android currency converter.
//
//  Copyright (C) 2016	Bill Farmer
//
//  This program is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
//  Bill Farmer	 william j farmer [at] yahoo [dot] co [dot] uk.
//
///////////////////////////////////////////////////////////////////////////////

package org.billthefarmer.specie;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SpecieWidgetUpdate extends Service
    implements Data.TaskCallbacks
{
    public static final String TAG = "SpecieWidgetUpdate";

    private Data data;

    // onCreate
    @Override
    public void onCreate()
    {
        // Get data instance
        data = Data.getInstance(this);

        if (BuildConfig.DEBUG)
            Log.d(TAG, "onCreate " + data);
    }

    // onStartCommand
    @Override
    @SuppressWarnings("deprecation")
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if (BuildConfig.DEBUG)
            Log.d(TAG, "onStartCommand " + intent);

        // Start the task
        if (data != null)
            data.startParseTask(Main.DAILY_URL);

        else
        {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (BuildConfig.DEBUG)
            Log.d(TAG, "startParseTask");

        return START_NOT_STICKY;
    }

    // onBind
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    // onDestroy
    @Override
    public void onDestroy()
    {
        // Remove listener
        Data.getInstance(null);
    }

    // On progress update
    @Override
    public void onProgressUpdate(String... dates)
    {
        if (BuildConfig.DEBUG)
            Log.d(TAG, "onProgressUpdate " + dates[0]);

        SimpleDateFormat dateParser =
            new SimpleDateFormat(Main.DATE_FORMAT, Locale.getDefault());
        DateFormat dateFormat =
            DateFormat.getDateInstance(DateFormat.MEDIUM);
        String date = null;

        // Format the date for display
        if (dates[0] != null)
        {
            try
            {
                Date update = dateParser.parse(dates[0]);
                date = dateFormat.format(update);
            }

            catch (Exception e)
            {
                return;
            }
        }

        // Get preferences
        SharedPreferences preferences =
            PreferenceManager.getDefaultSharedPreferences(this);

        // Get editor
        SharedPreferences.Editor editor = preferences.edit();

        editor.putString(Main.PREF_DATE, date);
        editor.apply();
    }

    // The system calls this to perform work in the UI thread and
    // delivers the result from doInBackground()
    @Override
    @SuppressWarnings("deprecation")
    public void onPostExecute(Map<String, Double> map)
    {
        if (BuildConfig.DEBUG)
            Log.d(TAG, "onPostExecute " + map);

        // Check the map
        if (map.isEmpty())
        {
            stopSelf();
            return;
        }

        // Get preferences
        SharedPreferences preferences =
            PreferenceManager.getDefaultSharedPreferences(this);

        map.put("EUR", 1.0);

        // Get saved specie lists
        String namesJSON = preferences.getString(Main.PREF_NAMES, null);
        List<String> nameList = new ArrayList<String>();
        List<String> valueList = new ArrayList<String>();

        int digits = Integer.parseInt(preferences.getString
                                      (Main.PREF_DIGITS, "3"));
        // Set digits
        NumberFormat numberFormat = NumberFormat.getInstance();
        numberFormat.setMinimumFractionDigits(digits);
        numberFormat.setMaximumFractionDigits(digits);
        numberFormat.setGroupingUsed(true);

        // Check saved name list
        if (namesJSON != null)
        {
            try
            {
                // Update name list from JSON array
                JSONArray namesArray = new JSONArray(namesJSON);
                for (int i = 0; !namesArray.isNull(i); i++)
                    nameList.add(namesArray.getString(i));
            }

            catch (Exception e) {}
        }

        // Use the default list
        else
        {
            nameList.addAll(Arrays.asList(Main.SPECIE_LIST));
        }

        // Get current specie
        int currentIndex = preferences.getInt(Main.PREF_INDEX, 0);

        double currentValue = Double.parseDouble(preferences.getString
                                                 (Main.PREF_VALUE, "1.0"));
        String stringValue = numberFormat.format(currentValue);

        // Get the convert value
        double convertValue =
            map.containsKey(Main.SPECIES[currentIndex].name)?
            map.get(Main.SPECIES[currentIndex].name): Double.NaN;

        // Populate a new value list
        for (String name : nameList)
        {
            try
            {
                Double value = (currentValue / convertValue) *
                    map.get(name);

                valueList.add(numberFormat.format(value));
            }

            catch (Exception e)
            {
                valueList.add(numberFormat.format(Double.NaN));
            }
        }

        // Get editor
        SharedPreferences.Editor editor = preferences.edit();

        // Get entries
        JSONObject valueObject = new JSONObject(map);
        JSONArray valueArray = new JSONArray(valueList);

        // Update preferences
        editor.putString(Main.PREF_MAP, valueObject.toString());
        editor.putString(Main.PREF_VALUES, valueArray.toString());
        editor.apply();

        // Get manager
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        ComponentName provider = new
            ComponentName(this, SpecieWidgetProvider.class);

        int appWidgetIds[] = appWidgetManager.getAppWidgetIds(provider);
        for (int appWidgetId: appWidgetIds)
        {
            int widgetEntry = Integer.parseInt
                (preferences.getString(Main.PREF_ENTRY, "0"));

            widgetEntry = preferences.getInt(String.valueOf(appWidgetId),
                                             widgetEntry);

            if (widgetEntry >= nameList.size())
                widgetEntry = 0;

            String entryName = nameList.get(widgetEntry);
            String entryValue = valueList.get(widgetEntry);
            int entryIndex = Main.specieIndex(entryName);
            String longName = getString
                (Main.SPECIES[entryIndex].longname);

            // Create an Intent to configure widget
            Intent config = new Intent(this, SpecieWidgetConfigure.class);
            config.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            // This bit of jiggery hackery is to force the system to
            // keep a different intent for each widget
            Uri uri = Uri.parse(Main.WIDGET + String.valueOf(appWidgetId));
            config.setData(uri);
            //noinspection InlinedApi
            PendingIntent configIntent =
                PendingIntent.getActivity(this, 0, config,
                                          PendingIntent.FLAG_UPDATE_CURRENT |
                                          PendingIntent.FLAG_IMMUTABLE);
            // Create an Intent to launch Specie
            Intent intent = new Intent(this, Main.class);
            //noinspection InlinedApi
            PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, intent,
                                          PendingIntent.FLAG_UPDATE_CURRENT |
                                          PendingIntent.FLAG_IMMUTABLE);
            // Get the layout for the widget
            RemoteViews views = new
                RemoteViews(getPackageName(), R.layout.widget);

            // Attach an on-click listener to the view.
            views.setOnClickPendingIntent(R.id.widget, pendingIntent);
            views.setOnClickPendingIntent(R.id.config, configIntent);

            views.setTextViewText(R.id.current_name,
                                  Main.SPECIES[currentIndex].name);
            views.setTextViewText(R.id.current_symbol,
                                  Main.SPECIES[currentIndex].symbol);
            views.setTextViewText(R.id.current_value, stringValue);

            views.setImageViewResource(R.id.flag,
                                       Main.SPECIES[entryIndex].flag);
            views.setTextViewText(R.id.name, entryName);
            views.setTextViewText(R.id.symbol,
                                  Main.SPECIES[entryIndex].symbol);
            views.setTextViewText(R.id.value, entryValue);
            views.setTextViewText(R.id.long_name, longName);

            // Tell the AppWidgetManager to perform an update on the
            // current app widget.
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }

        stopSelf();
    }
}
