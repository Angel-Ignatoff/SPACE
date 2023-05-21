import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

public class ShuttleLaunchWeatherApp {
    public static void main(String[] args) {
        // Read command-line arguments
        String filePath = args[0];
        String senderEmail = args[1];
        String password = args[2];
        String receiverEmail = args[3];

        try {
            // Step 1: Parse the Weather Forecast CSV File
            List<String[]> weatherData = parseWeatherForecastFile(filePath);

            // Step 2: Apply Weather Criteria and Calculate Aggregates
            List<String[]> filteredData = applyWeatherCriteria(weatherData);
            List<String[]> aggregates = calculateAggregates(filteredData);

            // Step 3: Generate the Weather Report CSV File
            String reportFilePath = "WeatherReport.csv";
            generateWeatherReportFile(reportFilePath, aggregates);

            // Step 4: Send Email with Attachment
            sendEmailWithAttachment(senderEmail, password, receiverEmail, reportFilePath);

            System.out.println("Weather report generated and email sent successfully!");
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static List<String[]> parseWeatherForecastFile(String filePath) throws IOException {
        List<String[]> weatherData = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Split the line into an array of values
                String[] values = line.split(",");
                weatherData.add(values);
            }
        }

        return weatherData;
    }

    private static List<String[]> applyWeatherCriteria(List<String[]> weatherData) {
        List<String[]> filteredData = new ArrayList<>();

        // Iterate over each row in the weather data
        for (String[] row : weatherData) {
            String day = row[0];
            int temperature = Integer.parseInt(row[1]);
            int wind = Integer.parseInt(row[2]);
            int humidity = Integer.parseInt(row[3]);
            int precipitation = Integer.parseInt(row[4]);
            String lightning = row[5];
            String clouds = row[6];

            // Check if the row meets the weather criteria
            if (WeatherCriteria.isTemperatureInRange(temperature) &&
                    WeatherCriteria.isWindSpeedBelowThreshold(wind) &&
                    WeatherCriteria.isHumidityBelowThreshold(humidity) &&
                    WeatherCriteria.isPrecipitationMatch(precipitation) &&
                    WeatherCriteria.isLightningMatch(lightning) &&
                    WeatherCriteria.isCloudsMatch(clouds)) {
                filteredData.add(row);
            }
        }

        return filteredData;
    }

    private static List<String[]> calculateAggregates(List<String[]> filteredData) {
        List<String[]> aggregates = new ArrayList<>();

        // Get the number of parameters in the filtered data
        int numParameters = filteredData.get(0).length;

        // Iterate over each parameter column
        for (int i = 1; i < numParameters; i++) {
            String parameterName = filteredData.get(0)[i];
            List<Integer> parameterValues = new ArrayList<>();

            // Collect the values for the current parameter
            for (int j = 1; j < filteredData.size(); j++) {
                int value = Integer.parseInt(filteredData.get(j)[i]);
                parameterValues.add(value);
            }

            // Calculate the aggregates for the current parameter
            int sum = 0;
            int max = Integer.MIN_VALUE;
            int min = Integer.MAX_VALUE;

            for (int value : parameterValues) {
                sum += value;
                max = Math.max(max, value);
                min = Math.min(min, value);
            }

            double average = (double) sum / parameterValues.size();

            Collections.sort(parameterValues);
            double median;
            if (parameterValues.size() % 2 == 0) {
                int mid1 = parameterValues.size() / 2 - 1;
                int mid2 = parameterValues.size() / 2;
                median = (parameterValues.get(mid1) + parameterValues.get(mid2)) / 2.0;
            } else {
                int mid = parameterValues.size() / 2;
                median = parameterValues.get(mid);
            }

            // Create an array with the aggregates for the current parameter
            String[] aggregateRow = new String[]{
                    parameterName,
                    String.format("%.2f", average),
                    String.valueOf(max),
                    String.valueOf(min),
                    String.format("%.2f", median),
                    ""
            };

            aggregates.add(aggregateRow);
        }

        return aggregates;
    }

    private static void generateWeatherReportFile(String filePath, List<String[]> aggregates) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            // Write parameter names and aggregate values to the CSV file
            writer.write("Parameter,Average,Max,Min,Median,Launch Day\n");

            for (String[] aggregate : aggregates) {
                writer.write(String.join(",", aggregate) + "\n");
            }
        }
    }

    private static void sendEmailWithAttachment(String senderEmail, String password, String receiverEmail, String attachmentFilePath) {
        // SMTP server properties
        String host = "smtp.gmail.com";
        int port = 587;

        // Sender and receiver email addresses
        String from = senderEmail;
        String to = receiverEmail;

        // Create properties for the SMTP connection
        Properties properties = new Properties();
        properties.put("mail.smtp.host", host);
        properties.put("mail.smtp.port", port);
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");

        // Create a Session object with the authentication credentials
        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(senderEmail, password);
            }
        });

        try {
            // Create a MimeMessage object
            Message message = new MimeMessage(session);

            // Set the sender and receiver addresses
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));

            // Set the email subject
            message.setSubject("Weather Report");

            // Create the email body part
            BodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText("Please find the weather report attached.");

            // Create the attachment body part
            MimeBodyPart attachmentBodyPart = new MimeBodyPart();
            attachmentBodyPart.attachFile(attachmentFilePath);

            // Create a multipart message and add the parts to it
            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(messageBodyPart);
            multipart.addBodyPart(attachmentBodyPart);

            // Set the content of the message to the multipart message
            message.setContent(multipart);

            // Send the email
            Transport.send(message);

            System.out.println("Email sent successfully.");
        } catch (MessagingException | IOException e) {
            e.printStackTrace();
        }
    }
}

class WeatherCriteria {
    public static boolean isTemperatureInRange(int temperature) {
        return temperature >= 2 && temperature <= 31;
    }

    public static boolean isWindSpeedBelowThreshold(int windSpeed) {
        return windSpeed <= 20;
    }

    public static boolean isHumidityBelowThreshold(int humidity) {
        return humidity <= 80;
    }

    public static boolean isPrecipitationMatch(int precipitation) {
        return precipitation == 0;
    }

    public static boolean isLightningMatch(String lightning) {
        return lightning.equalsIgnoreCase("no");
    }

    public static boolean isCloudsMatch(String clouds) {
        return clouds.equalsIgnoreCase("clear") || clouds.equalsIgnoreCase("few clouds");
    }
}
