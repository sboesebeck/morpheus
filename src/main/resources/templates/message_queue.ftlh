<html>
<head>
    <#include "includes/includes.ftlh">
    <title>Welcome to Morpheus</title>
</head>
<body>

<#list queueByColl["mmsg_accqueue"] as msg>
        Name: ${msg.name} value: ${msg.value!} date: ${msg.time?string('dd.MM.yyyy HH:mm:ss')}  prio: ${msg.priority}<br>
    </#list>

</body>
</html>