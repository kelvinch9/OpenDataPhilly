package edu.upenn.cit594.processor;

import edu.upenn.cit594.datamanagement.CSVPropertiesReader;
import edu.upenn.cit594.datamanagement.PopulationFileReader;
import edu.upenn.cit594.datamanagement.Reader;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import edu.upenn.cit594.data.*;
import edu.upenn.cit594.data.Properties;

public abstract class Processor {

	protected Reader finesReader;
	protected Reader propertiesReader;
	protected Reader populationReader = new PopulationFileReader();
	protected Map<Integer, ZipCode> zipCodes;
	protected Map<Integer, LinkedList<Properties>> zipProperties;
	protected Map<Integer, LinkedList<ParkingFine>> zipParkingFines;
	
	///////////////Memoized Data///////////////
	protected int totalPopulation = 0;
	protected Map<Integer, String> finesPerCapita;
	protected Map<Integer, Integer> totalMarketvalue;
	protected Map<Integer, Integer> avgResidenceValue;
	protected Map<Integer, Integer> avgResidenceArea;
	
	
	public Processor() {
		finesReader = createReader();
		propertiesReader = new CSVPropertiesReader();
	}
	
	protected abstract Reader createReader();
	
	public void run() {
		zipProperties = new HashMap<>();
		zipParkingFines = new HashMap<>();
		zipCodes = new HashMap<>();
		totalMarketvalue = new HashMap<>();
		avgResidenceValue = new HashMap<>();
		avgResidenceArea = new HashMap<>();
		// DO WHATEVER ELSE
	}
	
	// Make the memo tables with key as valid zips
	public long makeZipKeys(String populationFilename) {
		long time = System.currentTimeMillis();
		List<ZipCode> populations = populationReader.read(populationFilename);
		for(ZipCode zip : populations) {
			zipCodes.put(zip.getCode(), zip);
			zipProperties.put(zip.getCode(), new LinkedList<>());
			zipParkingFines.put(zip.getCode(), new LinkedList<>());
		}
		return time;
	}
	
	// Place properties into correct key. Update zip instance variables in memo
	public long placeProperties(String propertiesFile) {
		long time = System.currentTimeMillis();
		List<Properties> properties = propertiesReader.read(propertiesFile);
		
		for(Properties property : properties) {
			int propertyCode = property.getZipcode();
			if(validZip(propertyCode)) {
				zipProperties.get(propertyCode).add(property); // APPEND TO LINKED LIST
				
				int currentTotalProperties = zipCodes.get(propertyCode).getTotalNumberProperties();
				double currentTotalPropertyLiveableArea = zipCodes.get(propertyCode).getTotalPropertyLivableArea();
				double currentTotalPropertyValue = zipCodes.get(propertyCode).getTotalPropertyValue();
				zipCodes.get(propertyCode).setTotalNumberProperties(currentTotalProperties + 1);
				zipCodes.get(propertyCode).setTotalPropertyLivableArea(currentTotalPropertyLiveableArea + property.getTotalLivableArea());
				zipCodes.get(propertyCode).setTotalPropertyValue(currentTotalPropertyValue + property.getMarketValue());
			}
		}
		return time;
	}
	
	// Place parkingfines into correct key. Update zip instance varibles in memo
	public long placeParkingFines(String finesFile) {
		long time = System.currentTimeMillis();
		List<ParkingFine> parkingFines = finesReader.read(finesFile);
		for(ParkingFine parkingFine : parkingFines) {
			
			int propertyCode = parkingFine.getZipcode();
			
			//Check that zip is valid and license plate is PA
			if(validZip(propertyCode) && (parkingFine.getStateLicensePlate().equals("PA"))) {
				zipParkingFines.get(propertyCode).add(parkingFine); // APPEND TO LINKED LIST

				int currentTotalParkingTickets = zipCodes.get(propertyCode).getTotalParkingTickets(); //memoized running total tickets
				double currentTotalFines = zipCodes.get(propertyCode).getTotalParkingTicketFines(); //memoozed running total fine amount
				double currentPropertyFine = parkingFine.getFine(); //current property's fine amount
				zipCodes.get(propertyCode).setTotalParkingTickets(currentTotalParkingTickets + 1); //update running total on memoized object
				zipCodes.get(propertyCode).setTotalParkingTicketFines(currentTotalFines + currentPropertyFine); //update running total fine amount on memoized object
			}
		}
		return time;
	}
	
	public int calculateTotalPopulation() {
		//check if value for zipcode was pre-computed.
		if(totalPopulation != 0) {
			System.out.println("taking from memoized data"); //for testing purposes
			return totalPopulation;
		}
		
		int output = 0;
		for(int i : zipCodes.keySet()) {
			output += zipCodes.get(i).getTotalPopulation();
		}
		
		totalPopulation = output; //memoize
		return output;
	}
	
	
	// NUMBER 2
	public Map<Integer, String> calculateTotalFinesPerCapita() {		
		//check if value for zipcode was pre-computed.
		if(finesPerCapita != null) {
			System.out.println("taking from memoized data"); //for testing purposes
			return finesPerCapita;
		}
		
		// must be written in ascending numerical order, 4 digits after decimal point
		Map<Integer, String> output = new TreeMap<>();
		for(int z : zipCodes.keySet()) {
			double finesPerCapita = zipCodes.get(z).getTotalParkingTicketFines() / zipCodes.get(z).getTotalPopulation();
			if(finesPerCapita > 0.00009) {
				output.put(z, truncate(finesPerCapita));
			}
		}
		finesPerCapita = output; //memoize
		return output;
	}
	
	// NUMBER 3 && NUMBER 4
	public int calculateRatio(Strategy strategy, int zipcode) {
		//check if value for zipcode was pre-computed.
		if(avgResidenceArea.containsKey(zipcode) && strategy.getStrategyType().equals("area")) {
			System.out.println("taking from memoized data"); //for testing purposes
			return avgResidenceArea.get(zipcode);
		} else if(avgResidenceValue.containsKey(zipcode) && strategy.getStrategyType().equals("value")) {
			System.out.println("taking from memoized data"); //for testing purposes
			return avgResidenceValue.get(zipcode);
		}
		if(!validZip(zipcode)) {
			return 0;
		}
		
		ZipCode target = zipCodes.get(zipcode);
		double Numerator = strategy.getNumerator(target);
		double Denominator = strategy.getDenominator(target);
		int output = truncateDiv(Numerator, Denominator);
		
		//memoize
		if(strategy.getStrategyType().equals("area" )) {
			avgResidenceArea.put(zipcode, output);
		} else {
			avgResidenceValue.put(zipcode, output);
		}
		return output;
	}
	
	
	// NUMBER 5
	public int calculateTotalResidentialMarketValuePerCapita(int zipCode) {
		//check if value for zipcode was pre-computed.
		if(totalMarketvalue.containsKey(zipCode)) {
			System.out.println("taking from memoized data"); //for testing purposes
			return totalMarketvalue.get(zipCode);
		}
		if(!validZip(zipCode)) {
			return 0;
		}
		int output = (int) Math.floor(zipCodes.get(zipCode).getTotalPropertyValue() / zipCodes.get(zipCode).getTotalPopulation());
		totalMarketvalue.put(zipCode, output);//memoize
		return output;
	}
	
	public boolean validZip(int zipCode) {
		return zipProperties.containsKey(zipCode);
	}
	
	///////////////private helper methods///////////////
	private static String truncate(double d) {
		DecimalFormat df = new DecimalFormat("#.####");
		df.setRoundingMode(RoundingMode.FLOOR);
		return df.format(d);
	}
	
	private static int truncateDiv(double d1, double d2) {
		return (int) Math.floor(d1 / d2);
	}
	
	public static void main(String[] args) {
		
		double x1 = 0.119999;
		double x2 = 0.119999;
		System.out.println(truncate(x1));
	}
	
}
