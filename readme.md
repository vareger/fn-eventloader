mvn package -Dmaven.test.skip=true  
docker build -t eventloader ./  
docker tag eventloader vareger/event-loader:1.0.0  
docker tag eventloader vareger/event-loader:latest  
docker push vareger/event-loader:latest  