$ErrorActionPreference='Stop'
$freshDb='qa_kafka_'+[guid]::NewGuid().ToString('N').Substring(0,12)
if($freshDb -notmatch '^qa_kafka_[0-9a-f]{12}$'){throw 'Unsafe test database name'}
$containerCreated=$false
$schemaCreated=$false
$oldPassword=$env:SPRING_DATASOURCE_PASSWORD
function RootSql($sql){
    $sql | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'
    if($LASTEXITCODE -ne 0){throw 'Temporary database SQL failed'}
}
try{
    RootSql "CREATE DATABASE $freshDb; GRANT ALL PRIVILEGES ON $freshDb.* TO 'academy'@'%';"
    $schemaCreated=$true
    $env:SPRING_DATASOURCE_PASSWORD=(docker compose exec -T mysql printenv MYSQL_PASSWORD).Trim()
    if($LASTEXITCODE -ne 0){throw 'Cannot obtain local test datasource configuration'}
    docker run -d --name $freshDb --network codepath-academy_default --memory=640m --cpus=1 `
        -e SPRING_DATASOURCE_PASSWORD -e SPRING_DATASOURCE_USERNAME=academy `
        -e "SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/${freshDb}?useSSL=false&allowPublicKeyRetrieval=true" `
        -e APP_DEMODATA=true -e SPRING_DATA_REDIS_HOST=redis `
        -e SPRING_MAIL_HOST=mailpit -e SPRING_MAIL_PORT=1025 `
        -e MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always `
        -e AI_SEARCH_ENABLED=false -e AI_ASSISTANT_ENABLED=false -e AI_INDEXING_ENABLED=false `
        -e 'JAVA_TOOL_OPTIONS=-Xmx256m -XX:MaxMetaspaceSize=192m' codepath-academy-backend:latest | Out-Null
    if($LASTEXITCODE -ne 0){throw 'Cannot create isolated fresh-schema check'}
    $containerCreated=$true
    $ready=$false
    for($i=0;$i -lt 90;$i++){
        $tables="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$freshDb' AND table_name IN ('courses','ai_index_jobs');"
        $tableCount=$tables | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N'
        if($LASTEXITCODE -ne 0){throw 'Cannot inspect isolated test schema'}
        if($tableCount -ne '2'){Start-Sleep -Seconds 2;continue}
        $count="SELECT IF((SELECT COUNT(*) FROM ${freshDb}.courses)=3 AND (SELECT COUNT(*) FROM ${freshDb}.ai_index_jobs)=3, 'READY', 'WAIT');"
        $value=$count | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N' 2>$null
        if($LASTEXITCODE -eq 0 -and $value -eq 'READY'){$ready=$true;break}
        Start-Sleep -Seconds 2
    }
    if(-not $ready){docker logs --tail 50 $freshDb;throw 'Fresh migration/seeder did not complete'}
    $healthy=$false
    for($i=0;$i -lt 30;$i++){
        $ErrorActionPreference='Continue'
        docker exec $freshDb wget -q -O /dev/null http://127.0.0.1:9091/actuator/health 2>$null
        $healthExit=$LASTEXITCODE
        $ErrorActionPreference='Stop'
        if($healthExit -eq 0){$healthy=$true;break}
        Start-Sleep -Seconds 2
    }
    if(-not $healthy){
        docker exec $freshDb sh -c 'printf "GET /actuator/health HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n" | nc 127.0.0.1 9091'
        throw 'Fresh application health failed'
    }
    RootSql "SELECT version,success FROM ${freshDb}.flyway_schema_history ORDER BY installed_rank; SELECT course_id,revision,indexed_revision FROM ${freshDb}.ai_index_jobs;"
    Write-Host 'PASS: fresh MySQL migrations V1-V8, demo seed and transactional outbox; AI disabled needs no Ollama/Qdrant.'
}finally{
    if($containerCreated){docker rm -f $freshDb | Out-Null}
    if($schemaCreated){RootSql "REVOKE ALL PRIVILEGES ON $freshDb.* FROM 'academy'@'%'; DROP DATABASE $freshDb;"}
    $env:SPRING_DATASOURCE_PASSWORD=$oldPassword
}
