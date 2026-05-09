

<div id="errors">
            <span class="ui-state-error-text" v-for="(txt, index) in errorText">
                {{index+1}}: {{ txt }}    <button @click="remove(index)">ok</button> <br>
            </span>
</div>



<script type="text/javascript">
    const errorModel={
        data() {
            return {
                error: false,
                errorText: [],

            };
        },
        mounted(){
            console.log("Mounted!");
            this.getErrors();
            setInterval(this.getErrors,10000);
        },
        methods:{
            remove(i){
                fetch("/removeError?index="+i, {
                    "method": "GET",
                    "headers": {
                    }
                })
                    .then(response => {
                        if(response.ok){
                            return response.json()
                        } else{
                            alert("Server returned " + response.status + " : " + response.statusText);
                        }
                    })
                    .then(response => {
                        this.error = response.errorFlag;
                        this.errorText=response.errorText;
                        //translate autoconnect...
                        //     for (let i = 0; i < this.configs.length; i++) {
                        //         configs[i].autoConnect=(configs[i].autoConnect==="true");
                        //     }
                    })
                    .catch(err => {
                        console.log(err);
                    });
            },
            getErrors(){
                fetch("/errors", {
                    "method": "GET",
                    "headers": {
                    }
                })
                    .then(response => {
                        if(response.ok){
                            return response.json()
                        } else{
                            alert("Server returned " + response.status + " : " + response.statusText);
                        }
                    })
                    .then(response => {
                        this.error = response.errorFlag;
                        this.errorText=response.errorText;
                        //translate autoconnect...
                        //     for (let i = 0; i < this.configs.length; i++) {
                        //         configs[i].autoConnect=(configs[i].autoConnect==="true");
                        //     }
                    })
                    .catch(err => {
                        console.log(err);
                    });
            }

        }
    };

    const errorApp=Vue.createApp(errorModel);
    errorApp.mount("#errors");
</script>

