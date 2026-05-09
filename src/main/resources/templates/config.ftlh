<!DOCTYPE html>
<html lang="en">

<head>
    <#include "includes/header.ftlh">
</head>

<body id="page-top">

<!-- Page Wrapper -->
<div id="wrapper">

    <#include "includes/sidebar.ftlh">
    <!-- Content Wrapper -->
    <div id="content-wrapper" class="d-flex flex-column">

        <#include "includes/topbar.ftlh"/>
        <!-- Main Content -->
        <div id="content">

            <!-- Begin Page Content -->
            <div class="container-fluid">

                <div class="container">
                    <#if emptyconfig>
                        Empty config... please add at least one!
                    </#if>

                    <div id="contents">
                        <ul>
                            <li style="list-style-type:none" v-for="config in configs">
                                <config-card
                                        v-bind:config="config"
                                        v-bind:key="config.id">
                                </config-card>
                            </li>
                            <br>
                        </ul>
                        <button @click="add">add</button>
                        <button @click="store">store</button>
                    </div>

                </div>

            </div>
            <!-- /.container-fluid -->

        </div>
        <!-- End of Main Content -->

        <!-- Footer -->
        <footer class="sticky-footer bg-white">
            <div class="container my-auto">
                <div class="copyright text-center my-auto">
                    <span>CopyLeft &copy; caluga.de 2021</span>
                </div>
            </div>
        </footer>
        <!-- End of Footer -->

    </div>
    <!-- End of Content Wrapper -->

</div>
<!-- End of Page Wrapper -->

<!-- Scroll to Top Button-->
<a class="scroll-to-top rounded" href="#page-top">
    <i class="fas fa-angle-up"></i>
</a>


<script type="text/javascript">
    const configs = {
        data() {
            return {
                configs: []
            };
        },
        mounted() {
            console.log("Mounted!");
            this.getConfigs();
        },
        methods: {
            toggleConnect(e) {
                console.log("E is " + e);
            },
            add() {
                var l = this.configs.length;
                l = l + 1;
                this.configs.push({
                    "id": '' + l,
                    "name": 'new ' + l,
                    "description": 'Description for config ' + l,
                    "hostSeed": "localhost:27017,localhost:27018",
                    database: "Test" + l,
                    collection: "coll" + l
                });
            },
            remove(id) {
                this.configs = this.configs.filter(function (item) {
                    return item.id !== id
                });
            },
            getConfigs() {
                fetch("/configs", {
                    "method": "GET",
                    "headers": {}
                })
                    .then(response => {
                        if (response.ok) {
                            return response.json()
                        } else {
                            alert("Server returned " + response.status + " : " + response.statusText);
                        }
                    })
                    .then(response => {
                        this.configs = response;
                        //translate autoconnect...
                        //     for (let i = 0; i < this.configs.length; i++) {
                        //         configs[i].autoConnect=(configs[i].autoConnect==="true");
                        //     }
                    })
                    .catch(err => {
                        console.log(err);
                    });
            },
            store() {

                var xhr = new XMLHttpRequest();
                xhr.open("POST", "/configs");

                xhr.setRequestHeader("Accept", "application/json");
                xhr.setRequestHeader("Content-Type", "application/json");

                xhr.onreadystatechange = function () {
                    if (xhr.readyState === 4) {
                        console.log(xhr.status);
                        console.log(xhr.responseText);
                    }
                };

                var data = "{ \"data\" : " + JSON.stringify(this.configs) + " }";
                xhr.send(data);
            }
        }
    };
    // var app = new Vue({
    //     el: '#contents',
    //
    //     methods: {
    //         toggleConnect: function (e){
    //             console.log("e is: "+e)
    //         },
    //         add: function () {
    //             var l = this.configs.length;
    //             l = l + 1;
    //             this.configs.push({"id": '' + l, "name": 'new ' + l, "description": 'Description for config ' + l,"hosts":"localhost:27017,localhost:27018",database:"Test"+l,collection:"coll"+l});
    //         },
    //         remove: function () {
    //             if (this.configs.length == 0) return;
    //             this.configs.pop();
    //         }
    //     }
    // });
    const app = Vue.createApp(configs);
    app.component("config-card", {
        props: ['config'],
        template: `<div class="card shadow mb-4">
                                <div class="card-header py-3">
                                    <h6 class="m-0 font-weight-bold text-primary"> {{ config.name }}</h6>
                                </div>
                                <b class="card-body">
                                    <div>
                                     Name: <input class="form-control bg-light border-1" v-model="config.name">
                                </div><br>
                                <div>
                                     Description: <input class="form-control bg-light border-1" v-model="config.description">
                                </div><br>
                                <div>
                                     Host Seed: <input class="form-control bg-light border-1"  v-model="config.hostSeed">
                                </div><br>
                                <div>
                                     database: <input class="form-control bg-light border-1" v-model="config.database">
                                </div><br>
                                <div>
                                     Collection: <input class="form-control bg-light border-1" v-model="config.collection">
                                </div>
                                <br>
                                <div>
                                     Auto connect: <input type="checkbox" class="custom-checkbox" v-model="config.autoConnect">
                                </div>
                                 <br>
                             <#if connected>
                                    <button v-if="config.id == ${connectionId}" v-on:click="toggleConnect(config.id)">
                                        disconnect
                                    </button>
                                    <button v-if="config.id != ${connectionId}" @click="remove(config.id)">remove
                                    </button>
                                <#else >
                                    <button v-on:click="connect(config.id)">connect</button>
                                    <button @click="remove(config.id)">remove</button>
                                </#if>
                                </div>
                    </div>`
    });
    app.mount("#contents");
</script>

<!-- Bootstrap core JavaScript-->
<script src="/vendor/jquery/jquery.min.js"></script>
<script src="/vendor/bootstrap/js/bootstrap.bundle.min.js"></script>

<!-- Core plugin JavaScript-->
<script src="/vendor/jquery-easing/jquery.easing.min.js"></script>

<!-- Custom scripts for all pages-->
<script src="/js/sb-admin-2.min.js"></script>

</body>
</html>