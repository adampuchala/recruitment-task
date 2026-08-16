# Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
FROM liquibase/liquibase:4.33.0
COPY changelog /liquibase/changelog
ENTRYPOINT ["liquibase", "--search-path=/liquibase/changelog", "--changelog-file=db.changelog-master.yaml", "update"]
