FROM eclipse-temurin:21-jdk-jammy

# Copy the built application
COPY server/build/install/server /app

# Set the working directory
WORKDIR /app

# Expose the port the application runs on
EXPOSE 8080

# Run the application
CMD ["bin/server"]
