package market.stock.functions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import market.stock.functions.helper.Errors;

public class GetHistoricalPrices {

	private static final File prices = new File("src/market/stock/sector/data/historic_prices.txt");
	private static final String INIT_STRING = "http://finance.google.com/finance/historical?q=";

	private static Map<String, Map<String, Double>> data;
	static {
		data = new HashMap<>();
		try {
			data = getMapFromFile(prices);
		} catch (Exception e) {
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Map<String, Double>> getMapFromFile(File file) throws Exception {
		Map<String, Map<String, Double>> e;
		FileInputStream fis = new FileInputStream(file);
		ObjectInputStream ois = new ObjectInputStream(fis);
		e = (Map<String, Map<String, Double>>) ois.readObject();
		ois.close();
		fis.close();
		System.out.println("Map<String,Double> obtained from file");
		return e;
	}

	public static void storeMapFile() throws Exception {
		prices.createNewFile();
		FileOutputStream fos = new FileOutputStream(prices, false);
		ObjectOutputStream oos = new ObjectOutputStream(fos);
		oos.writeObject(data);
		oos.close();
		fos.close();
		System.out.println("Map<String, Map<String,Double> stored -> PRICES");
	}

	// increase data points a bit + 7 in both directions
	public static Map<String, Double> getPricesAccordingToDates(String stock, Date d1, Date d2) {
		SimpleDateFormat format = new SimpleDateFormat("MMM+dd+yyyy");
		String uri = INIT_STRING + stock + "&startdate=" + format.format(Week(d1, -7)) + "&enddate="
				+ format.format(Week(d2, 7)) + "&output=csv";
		try {
			if (data.containsKey(uri)) {
				System.out.println("Got " + stock + " data from file");
				return data.get(uri);
			}

			URL url = new URL(uri);
			BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
			ArrayList<String> in = new ArrayList<>();
			String XXX = br.readLine();
			while ((XXX = br.readLine()) != null) {
				in.add(XXX);
			}

			Map<String, Double> ret = new HashMap<>();
			for (String d : in) {
				String[] split = d.split(",");
				ret.put(split[0], Double.parseDouble(split[split.length - 2]));
			}

			data.put(uri, ret);
			return ret;
		} catch (MalformedURLException e) {
			Errors.errors.add(e.toString());
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			Errors.errors.add(e.toString());
			e.printStackTrace();
			return null; // error return null
		}
	}

	private static Date Week(Date d1, int w) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(d1);
		calendar.add(Calendar.DATE, w);
		return calendar.getTime();
	}

	public static Double[] getPricesAccordingToDates(String STOCK, Date[] obt) {
		Map<String, Double> data = getPricesAccordingToDates(STOCK, obt[0], obt[obt.length - 1]);
		if (data == null)
			return null;
		if (data.size() < obt.length)
			return null;

		// KEYSET IN TERMS OF: 1-Oct-17 // SO dd-MMM-yy
		SimpleDateFormat format = new SimpleDateFormat("d-MMM-yy");
		Double[] ret = new Double[obt.length];
		for (int i = 0; i < obt.length; i++) {
			Date date = obt[i];
			ret[i] = data.get(format.format(date));
			int incr = -1;
			while (ret[i] == null) {
				Calendar c = Calendar.getInstance();
				c.setTime(date);
				c.add(Calendar.DATE, incr);
				date = c.getTime();
				obt[i] = date;
				ret[i] = data.get(format.format(date));
			}
		}

		return ret;

	}

}
