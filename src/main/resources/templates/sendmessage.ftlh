html>
<head>
    <#include "includes/includes.ftlh">
    <title>Welcome to Morpheus</title>
</head>
<body>
<div class="container">


    <div id="sendmessage">

    </div>

</div>

<script type="text/javascript">
    const data= {
        data() {
            return {
                types:[],
                fields:[]
            };
        },
        mounted(){
            console.log("Mounted!");
        },
        methods: {
           send(){

           }
        }
    };

    const app= Vue.createApp(data);

    app.mount("#sendmessage");
</script>
</body>
</html>