package market.stock.sector;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import market.data_retrieval.Stock;
import market.data_retrieval.StockData;
import market.statistics.BasicFunctions;
import market.stock.functions.EarningsFinder;
import market.stock.functions.GetHistoricalPrices;
import market.stock.functions.helper.DateObtainer;
import market.stock.functions.helper.DatePrices;
import market.stock.functions.helper.Errors;
import market.stock.index.IndexData;

public class Data {
	private static final SimpleDateFormat defaultFormat = new SimpleDateFormat("MMM/dd/yy");

	private static final File UPDATE = new File("src/market/stock/sector/data/date.txt");

	private static final File SECTOR_FILE = new File("src/market/stock/sector/data/sector.txt");
	private static final File PREV_CLOSE_FILE = new File("src/market/stock/sector/data/prev_close.txt");
	private static final File SUBSECTOR_FILE = new File("src/market/stock/sector/data/subsector.txt");

	public static final String STOCKS = "https://old.nasdaq.com/screening/companies-by-industry.aspx?render=download";

	// Other stock lists: https://stooq.com/db/h/

	// STOCK LIST NASDAQ: https://stooq.com/db/l/?g=27
	// STOCK LIST NYSE: https://stooq.com/db/l/?g=28

	public static Map<String, ArrayList<String>> SECTORS = new HashMap<>(); // <String, ArrayList<String>> = Sector:
																			// Stocks in sector
	public static Map<String, ArrayList<String>> SUBSECTORS = new HashMap<>(); // <String, ArrayList<String>> =
																				// Subsector: Stocks in subsector
	public static Map<String, Double> PREV_CLOSE = new HashMap<>(); // <String, Double> = Stock: Prev Close

	static {
		SECTOR_FILE.setReadable(true);
		PREV_CLOSE_FILE.setReadable(true);
		SUBSECTOR_FILE.setReadable(true);

		try {
			if (!update())
				throw new AssertionError("Don't reinitialize");
			URL stockURL = new URL(STOCKS);
			BufferedReader in = new BufferedReader(new InputStreamReader(stockURL.openStream()));
			ArrayList<String[]> data = new ArrayList<>();
			String x = null;
			while ((x = in.readLine()) != null) {
				data.add(replace("\"", x.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)")));
			}

			System.out.println(data.size());

			/*
			 * data: 0: Symbol 1: Name 2: LastSale 3: MarketCap 4: ADR TSO 5: IPOyear 6:
			 * Sector 7: Industry 8: Summary Quote
			 */

			System.out.println("Updating indicies");
			IndexData.updateIndices();

			// FIRST ADD SECTORS
			System.out.println("Adding sectors");
			for (int i = 1; i < data.size(); i++) {
				String stock = data.get(i)[0].trim();
				String sector = data.get(i)[6].trim();
				ArrayList<String> init = new ArrayList<>();
				if (SECTORS.containsKey(sector)) {
					SECTORS.get(sector).add(stock);
				} else {
					init.add(stock);
					SECTORS.put(sector, init);
				}
			}
			// DONE

			// NEXT ADD SUBSECTORS
			System.out.println("Adding subsectors");
			for (int i = 1; i < data.size(); i++) {
				String stock = data.get(i)[0].trim();
				String subsector = data.get(i)[7].trim();
				ArrayList<String> init = new ArrayList<>();
				if (SUBSECTORS.containsKey(subsector)) {
					SUBSECTORS.get(subsector).add(stock);
				} else {
					init.add(stock);
					SUBSECTORS.put(subsector, init);
				}
			}
			// DONE

			// NEXT ADD PREV_CLOSE
			System.out.println("Adding prev_close");
			for (int i = 1; i < data.size(); i++) {
				if (SECTORS.containsKey(data.get(i)[6])) {
					PREV_CLOSE.put(data.get(i)[0].trim(),
							data.get(i)[2].equals("n/a") ? -1 : Double.parseDouble(data.get(i)[2]));
				}
			}
			// DONE
			
			STORE_ALL(); // store all data now loaded
		} catch (AssertionError e) {
			// Obtained cached data.
			try {
				SECTORS = getMapFromFile(SECTOR_FILE);
				SUBSECTORS = getMapFromFile(SUBSECTOR_FILE);
				PREV_CLOSE = getMapFromFileDouble(PREV_CLOSE_FILE);
			} catch (Exception e2) {
				e2.printStackTrace();
			}
		} catch (Exception e) {
			System.out.println("Error when parsing file");
			e.printStackTrace();
		}

		System.out.print("\n\n");
	}

	public static void storeDate(File update) {
		try {
			update.createNewFile();
			BufferedWriter bw = new BufferedWriter(new FileWriter(update));
			SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy");
			String curr_date = format.format(new Date());
			System.out.println("Storing new date.");
			bw.write(curr_date);
			bw.close();
		} catch (IOException e) {
			System.out.println("Failed to update date file.");
			e.printStackTrace();
		}
	}

	private static boolean update() {
		if (UPDATE.exists()) {
			try {
				BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(UPDATE)));
				String file_date = br.readLine();
				System.out.println("Currently updated for " + file_date);
				SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy");
				String curr_date = format.format(Calendar.getInstance().getTime());
				if (file_date.equals(curr_date)) {
					System.out.println("Not Updating");
					br.close();
					return false;
				}
				br.close();
			} catch (Exception e) {
				System.out.println("Updating");
				return true;
			}
		}
		System.out.println("Updating");
		return true;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Double> getMapFromFileDouble(File file) throws Exception {
		Map<String, Double> e;
		FileInputStream fis = new FileInputStream(file);
		ObjectInputStream ois = new ObjectInputStream(fis);
		e = (Map<String, Double>) ois.readObject();
		ois.close();
		fis.close();
		System.out.println("Map<String,Double> obtained from file");
		return e;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, ArrayList<String>> getMapFromFile(File file) throws Exception {
		Map<String, ArrayList<String>> e;
		FileInputStream fis = new FileInputStream(file);
		ObjectInputStream ois = new ObjectInputStream(fis);
		e = (Map<String, ArrayList<String>>) ois.readObject();
		ois.close();
		fis.close();
		System.out.println("Map<String,ArrayList<String>> obtained from file");
		return e;
	}

	public static void storeMapFile(Map<String, ArrayList<String>> map, File file) throws Exception {
		file.createNewFile();
		FileOutputStream fos = new FileOutputStream(file, false);
		ObjectOutputStream oos = new ObjectOutputStream(fos);
		oos.writeObject(map);
		oos.close();
		fos.close();
		System.out.println("Map<String, String> stored");
	}

	public static void storeMapFileDouble(Map<String, Double> mapStringDouble, File prevCloseFile) throws Exception {
		prevCloseFile.createNewFile();
		FileOutputStream fos = new FileOutputStream(prevCloseFile);
		ObjectOutputStream oos = new ObjectOutputStream(fos);
		oos.writeObject(mapStringDouble);
		oos.close();
		fos.close();
		System.out.println("Map<String, Double> stored");
	}

	private static String[] replace(String string, String[] split) {
		String[] ret = new String[split.length];
		for (int i = 0; i < split.length; i++) {
			ret[i] = split[i].replace("\"", "");
		}
		return ret;
	}

	public static void log(Object[] strings) {
		for (int i = 0; i < strings.length; i++) {
			System.out.print((strings[i] != null ? strings[i] : "null") + ",");
		}
		System.out.println();
	}
	
	static Scanner scanner = new Scanner(System.in);

	@SuppressWarnings("all")
	public static void main(String[] args) {
		System.out.println("SECTORS:: " + Data.SECTORS.keySet());
		System.out.println("SUBSECTORS:: " + Data.SUBSECTORS.keySet());

		System.out.print("\n");

		String input = "";
		while (true) {
			input = scanner.nextLine();
			if (input.equals("exit"))
				break;
			switch (input.trim()) {
			case "all":
				for (String sector : SECTORS.keySet()) {
					ArrayList<String> data = SECTORS.get(sector);
					System.out.println("STARTED SECTOR " + sector);
					getData(data);
					System.out.println("FINISHED SECTOR " + sector);
					STORE_ALL();
				}
				break;
			case "1":
				System.out.println("Input a sector: ");
				String sector = scanner.nextLine().trim();
				getData(Data.SECTORS.get(sector));
				System.out.println("FINISHED SUBSECTOR");
				break;
			case "2":
				System.out.println("Input a subsector: ");
				String subsector = scanner.nextLine();
				getData(Data.SUBSECTORS.get(subsector));
				System.out.println("FINISHED SUBSECTOR");
				break;
			case "3":
				getIndividualStock(scanner);
				break;
			case "0":
				obtain_date_correlating_price_data();
				break;
			case "s":
			case "sector":
			case "slist":
			case "list sector":
			case "list s":
			case "lists":
				// FOR LISTING SECTORS
				for (String s : SECTORS.keySet()) {
					System.out.println("Sector: " + s + " " + SECTORS.get(s).size());
				}
				break;
			case "ss":
			case "subsector":
			case "list subsector":
			case "list ss":
			case "listss":
				// FOR LISTING SUBSECTORS
				for (String s : SUBSECTORS.keySet()) {
					System.out.println("Subsector: " + s + " " + SUBSECTORS.get(s).size());
				}
			case "histp":
				System.out.println(DatePrices.DATE_TO_PRICE.size());
				print_to_file(DatePrices.printPrices(),
						"src/market/stock/sector/print/HistoricalPrices.txt");
				break;
			case "5": // THIS WILL BE PRINTING A SPECIFIC SECTOR'S RETURNS TO A FILE
				System.out.println("Input a sector: ");
				String sectorR = scanner.nextLine().trim();
				ArrayList<String> dataSR = Data.SECTORS.get(sectorR);
				if(dataSR == null) {
					System.out.println("Invalid Sector");
					break;
				}
				String result = printReturnsForSector(sectorR);
				print_to_file(result, "src/market/stock/sector/print/" + sectorR.replace(" ", "_") + "_returns.txt");
				break;
			case "r":
			case "rc":
			case "returns":
			case "print returns":
			case "print r":
				print_to_file(print_returns_for_all_data(false),
						"src/market/stock/sector/print/RETURNS_CUMULATIVE.txt");
				break;
			case "rp":
				print_to_file(print_returns_for_all_data(true), "src/market/stock/sector/print/RETURNS_PERIODIC.txt");
				break;
			case "help":
			case "h":
				System.out.println("MAKE A HELP SLIDE ");
				break;
			case "Find stock data":
			case "fsd":
			case "8":
				returnStockData();
			}
			STORE_ALL();
			System.out.println("--------------------------------------------------------------------------------------------------------------\n");
		}
		scanner.close();
	}

	/**
	 * Prints the first input to the File given by the second input
	 * 
	 * @param string
	 *            String that is to printed to given file name
	 * @param string2
	 *            File
	 */
	public static void print_to_file(String string, String string2) {
		try {
			PrintWriter out = new PrintWriter(string2);
			out.println(string);
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	private static void getData(ArrayList<String> stocks) {
		if (stocks == null)
			return;
		for (String s : stocks) {
			System.out.println("NEXT STOCK " + s);
			EarningsFinder ef = new EarningsFinder(s);
			Date[] dates = ef.getEarningsDates();
			for (Date date : dates) {
				DateObtainer dateObt = new DateObtainer(date);
				Date[] obt = dateObt.getDates();
				if (obt == null)
					continue;

				Double[] x = GetHistoricalPrices.getPricesAccordingToDates(ef.STOCK, obt);
				if (x == null)
					continue;
				System.out.println("Earnings: " + defaultFormat.format(date));

				for (Date d : obt) {
					System.out.print(defaultFormat.format(d) + "\t");
				}
				System.out.print("\n");
				for (Double d : x) {
					System.out.print(d + "\t\t");
				}
				System.out.println("\n");
			}
		}
	}

	private static void getIndividualStock(Scanner scanner) {
		System.out.println("Input a stock: ");
		String stock = scanner.nextLine();
		EarningsFinder ef = new EarningsFinder(stock);
		Date[] dates = ef.getEarningsDates();
		for (Date date : dates) {
			DateObtainer dateObt = new DateObtainer(date);
			Date[] obt = dateObt.getDates();
			if (obt == null)
				continue;
			Double[] x = GetHistoricalPrices.getPricesAccordingToDates(ef.STOCK, obt);
			if (x == null)
				continue;

			System.out.println("Earnings: " + defaultFormat.format(date));
			for (Date d : obt) {
				System.out.print(defaultFormat.format(d) + "\t");
			}
			System.out.print("\n");
			for (Double d : x) {
				System.out.print(d + "\t\t");
			}
			System.out.println("\n");
		}
	}

	/*
	 * So gets the hashmap from EarningsFinder For all stocks that have data (w/
	 * Date[].length > 0) obtain the dates -> Date[] and then GetHistoricalPrices ->
	 * Double[]
	 * 
	 * return the hashmap -> <Symbol, Map<Date[], Double[]> >
	 */
	public static void obtain_date_correlating_price_data() {
		Map<String, Date[]> earningsDates = EarningsFinder.x;
		Map<String, Map<Date[], Double[]>> ret = new HashMap<>();
		for (String sym : earningsDates.keySet()) {
			ret.put(sym, new HashMap<>());
			Date[] p1 = earningsDates.get(sym);
			for (Date date : p1) {
				DateObtainer dateObt = new DateObtainer(date);
				Date[] obt = dateObt.getDates();
				if (obt == null)
					continue;
				String uri = GetHistoricalPrices.construct_uri(sym, obt[0], obt[obt.length - 1]);
				Map<String, Double> data = GetHistoricalPrices.data.get(uri);
				Double[] x = GetHistoricalPrices.getPricesAccordingToDates(data, sym, obt);
				ret.get(sym).put(obt, x);
			}
		}

		DatePrices.DATE_TO_PRICE = ret;

	}

	public static String print_returns_for_all_data(boolean periodic) {
		ArrayList<double[]> returnsAL = new ArrayList<>();

		String ret = String.format("%8s\t%8s\t%8s\t%8s\t%8s\t%8s\t%8s\t%8s\t%8s\t%8s\t%8s\t\n", "", "", "", "", "", "",
				"EARNINGS", "", "", "", "");

		for (Map<Date[], Double[]> x : DatePrices.DATE_TO_PRICE.values()) {
			for (Date[] dates : x.keySet()) {
				Double[] d = x.get(dates);
				double[] returns = null;
				if (periodic)
					returns = BasicFunctions.printReturnsPerTimePeriod(d, dates);
				else
					returns = BasicFunctions.printReturnsCumulativePeriod(d, dates);
				if (returns != null) {
					returnsAL.add(returns);
					String y = BasicFunctions.toStringDoubleArrayReturns(returns);
					ret += y + "\n"; // PRINTING OUT (adding to string)
				}
			}
		}

		ret += "--------------------------------------------------------------------------------------------------------------\n";
		double[] average = BasicFunctions.averageArrayListOfDoubles(returnsAL);
		String average_string = BasicFunctions.toStringDoubleArrayReturns(average);
		ret += average_string;

		return ret;
	}
	
	public static String printReturnsForSector(String sector) {
		ArrayList<double[]> returnsAL = new ArrayList<>();

		String ret = String.format("%8s\t%8s\t%8s\t%8s\t%8s\t%8s\t%8s\t%8s\t%8s\t%8s\t%8s\t\n", "", "", "", "", "", "",
				"EARNINGS", "", "", "", "");

		ArrayList<String> sym = SECTORS.get(sector);
		for(String symbol : sym) {
			Map<Date[], Double[]> x = DatePrices.DATE_TO_PRICE.get(symbol);
			if(x == null) continue;
			for (Date[] dates : x.keySet()) {
				Double[] d = x.get(dates);
				double[] returns = BasicFunctions.printReturnsPerTimePeriod(d, dates);
				if (returns != null) {
					String y = BasicFunctions.toStringDoubleArrayReturns(returns);
					ret += y + "\n"; // PRINTING OUT (adding to string)
					returnsAL.add(returns);
				}
			}
		}
		
		ret += "--------------------------------------------------------------------------------------------------------------\n";
		double[] average = BasicFunctions.averageArrayListOfDoubles(returnsAL);
		String average_string = BasicFunctions.toStringDoubleArrayReturns(average);
		ret += average_string;
		
		return ret;
	}

	public static void STORE_ALL() {
		try {
			storeMapFile(SECTORS, SECTOR_FILE);
			storeMapFile(SUBSECTORS, SUBSECTOR_FILE);
			storeMapFileDouble(PREV_CLOSE, PREV_CLOSE_FILE);
			DatePrices.storeMapFile();
			storeDate(UPDATE);
			IndexData.storeIndices();
			GetHistoricalPrices.storeMapFile();
			EarningsFinder.storeMapFile();
			Errors.PRINT_ERRORS();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * TODO: ONLY GO THROUGH DATA THAT IS OBTAINED (not things that have not
	 * "obtained data") - create a HashMap for <Symbol, Date[]>. have that - go
	 * through that HashMap and online print those
	 * 
	 * TODO: Turn index data into weekly return and cumulative returns
	 * TODO: Subtract index returns to individual price returns
	 * 
	 * TODO: CREATE A CONSTANTS.java repository for hashmaps and such
	 */

	 public static boolean returnStockData() {
		System.out.println("Input Symbol to retrieve data");
		String nl = scanner.nextLine();
		nl = nl.toUpperCase();

		System.out.println("Trying to find data for " + nl);

		String sector = "";
		for (String sec : SECTORS.keySet()) {
			if (SECTORS.get(sec).contains(nl)) {
				System.out.println("Found " + nl + " Sector: " + sec);
				sector = sec;
				break;
			}
		}

		double prevClose = 0.0;
		if (PREV_CLOSE.containsKey(nl)) {
			prevClose = PREV_CLOSE.get(nl);
			System.out.println("Found " + nl + " Previous close: " + prevClose);
		}

		if (sector == "" || prevClose == 0.0) {
			System.out.println("Couldn't find some data...");
		}

		System.out.println("Please confirm that above data is correct for " + nl + " (y/n)");
		String tmp = scanner.nextLine();
		if (tmp.contains("y")) {
			System.out.println(SECTORS.get(sector));
			Stock s = StockData.CollectStockData(nl);
			if (s != null) {
				System.out.println(s);
			}
			return true; // ran tried to obtain data
		} else {
			System.out.println("Breaking back to start");
			return false;
		}
	 }

}
