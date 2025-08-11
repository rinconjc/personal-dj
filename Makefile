IMAGE=ai-dj

FRONTEND_FILES := $(shell find src -name "*.cljs")
BACKEND_FILES := $(shell find src -name "*.clj")
BACKEND_JAR := target/ai-dj-backend.jar
CLJS_OUTPUT := public/js/release/main.js

$(CLJS_OUTPUT): $(FRONTEND_FILES)
	npx shadow-cljs release app \
	--config-merge '{:output-dir "public/js/release" :closure-defines {ai-dj.core/BACKEND_URL ""}}'

frontend: $(CLJS_OUTPUT)

$(BACKEND_JAR): $(BACKEND_FILES)
	clojure -T:build uberjar

backend: $(BACKEND_JAR)

.build-docker: frontend backend $(FRONTEND_FILES)
	echo "building docker..."
	docker buildx build --platform=linux/amd64 -t $(IMAGE) .
	touch .build-docker
	echo "done"

.push-docker: .build-docker
	docker image save $(IMAGE) -o /tmp/$(IMAGE).tar
	scp /tmp/$(IMAGE).tar aka:/tmp/
	ssh aka 'docker image load -i /tmp/$(IMAGE).tar'
	touch .push-docker
	echo "docker pushed"

deploy: .push-docker
	ssh aka 'cd apps/ai-dj; docker-compose stop ai-dj; docker-compose up -d '

start:
	clojure -M:main
